package com.example.final_projects.service;

import com.example.final_projects.aop.HandleExternalApiErrors;
import com.example.final_projects.dto.PageResponse;
import com.example.final_projects.dto.template.*;
import com.example.final_projects.entity.*;
import com.example.final_projects.exception.AiException;
import com.example.final_projects.exception.RawExternalApiException;
import com.example.final_projects.exception.TemplateException;
import com.example.final_projects.exception.code.AiErrorCode;
import com.example.final_projects.exception.code.TemplateErrorCode;
import com.example.final_projects.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateHistoryRepository templateHistoryRepository;
    private final AiApiClient aiApiClient;
    private final UserTemplateRequestService userTemplateRequestService;
    private final TemplateFactory templateFactory;

    public TemplateService(
            TemplateRepository templateRepository,
            TemplateHistoryRepository templateHistoryRepository,
            AiApiClient aiApiClient,
            UserTemplateRequestService userTemplateRequestService,
            TemplateFactory templateFactory
    ) {
        this.templateRepository = templateRepository;
        this.templateHistoryRepository = templateHistoryRepository;
        this.aiApiClient = aiApiClient;
        this.userTemplateRequestService = userTemplateRequestService;
        this.templateFactory = templateFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateResponse> getTemplates(Long userId, TemplateStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);

        Page<Template> templatePage = templateRepository.findByUserIdAndStatus(userId, status, pageRequest);

        List<TemplateResponse> data = templatePage.getContent().stream()
                .map(TemplateResponse::from)
                .toList();

        return new PageResponse<>(data, page, size, templatePage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TemplateResponse getTemplateById(Long templateId, Long userId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않습니다"));

        if (template.getUserId() == null || !template.getUserId().equals(userId)) {
            throw new AccessDeniedException("권한이 없습니다");
        }
        return TemplateResponse.from(template);
    }

    @HandleExternalApiErrors(
            errorCodeClass = AiErrorCode.class,
            errorDtoClass = AiErrorResponse.class,
            exceptionClass = AiException.class
    )
    public TemplateCreationResult createTemplate(Long userId, TemplateCreateRequest request, String clientIp, String userAgent) {
        UserTemplateRequest userRequest = userTemplateRequestService.createInitialRequest(userId, request.getRequestContent());

        ResponseEntity<AiApiResponse<AiTemplateResponse>> responseEntity = aiApiClient.createTemplate(userRequest);

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return handleSuccessResponse(responseEntity, userRequest, userId);
        } else {
            AiErrorResponse rawError = responseEntity.getBody() != null ? responseEntity.getBody().error() : null;
            HttpStatusCode statusCode = responseEntity.getStatusCode();

            throw new RawExternalApiException(
                    HttpStatus.valueOf(statusCode.value()),
                    rawError,
                    "Fallback executed for user: " + userId
            );
        }
    }

    private TemplateCreationResult handleSuccessResponse(
            ResponseEntity<AiApiResponse<AiTemplateResponse>> responseEntity,
            UserTemplateRequest userRequest,
            Long userId) {

        AiApiResponse<AiTemplateResponse> body = responseEntity.getBody();
        if (body == null || body.data() == null) {
            throw new AiException(AiErrorCode.UNEXPECTED_AI_RESPONSE, "AI service returned a success status but with an empty body.");
        }
        AiTemplateResponse aiTemplateData = body.data();

        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            Template template = templateFactory.createFrom(userId, aiTemplateData, userRequest);
            saveTemplateHistory(template);
            userTemplateRequestService.markAsCompleted(userRequest.getId());
            return new TemplateCreationResult.Complete(TemplateResponse.from(template));

        } else if (responseEntity.getStatusCode() == HttpStatus.ACCEPTED) {
            return new TemplateCreationResult.Incomplete(aiTemplateData);
        }

        throw new AiException(AiErrorCode.UNEXPECTED_AI_RESPONSE, "Unexpected success status code: " + responseEntity.getStatusCode());
    }

    private void saveTemplateHistory(Template template) {
        templateHistoryRepository.save(
                TemplateHistory.builder()
                        .template(template)
                        .status(TemplateStatus.CREATED)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    @Transactional
    public TemplateApproveResponse approveTemplate(Long templateId, Long userId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateException(TemplateErrorCode.TEMPLATE_NOT_FOUND));

        if (!template.getUserId().equals(userId)) {
            throw new TemplateException(TemplateErrorCode.FORBIDDEN_TEMPLATE);
        }

        if (template.getStatus() == TemplateStatus.APPROVE_REQUESTED) {
            throw new TemplateException(TemplateErrorCode.ALREADY_APPROVE_REQUESTED);
        }

        if (template.getStatus() != TemplateStatus.CREATED) {
            throw new TemplateException(TemplateErrorCode.APPROVE_REQUEST_FORBIDDEN);
        }

        template.setStatus(TemplateStatus.APPROVE_REQUESTED);

        templateRepository.save(template);

        templateHistoryRepository.save(
                TemplateHistory.builder()
                        .template(template)
                        .status(TemplateStatus.APPROVE_REQUESTED)
                        .build()
        );

        return new TemplateApproveResponse(template.getId(), template.getStatus().name());
    }
}
