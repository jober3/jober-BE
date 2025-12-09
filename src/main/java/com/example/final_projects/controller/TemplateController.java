package com.example.final_projects.controller;

import com.example.final_projects.config.swagger.ApiErrorCodeExample;
import com.example.final_projects.dto.ApiResult;
import com.example.final_projects.dto.PageResponse;
import com.example.final_projects.dto.template.TemplateApproveResponse;
import com.example.final_projects.dto.template.TemplateCreateRequest;
import com.example.final_projects.dto.template.TemplateResponse;
import com.example.final_projects.dto.template.TemplateSearchRequest;
import com.example.final_projects.exception.code.TemplateErrorCode;
import com.example.final_projects.security.CustomUserPrincipal;
import com.example.final_projects.service.TemplateCreationResult;
import com.example.final_projects.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(
            summary = "템플릿 목록 조회",
            description = "사용자가 APPROVE_REQUESTED, APPROVED, REJECTED 상태의 템플릿을 조회"
    )
    @ApiErrorCodeExample(TemplateErrorCode.class)
    @GetMapping
    public ApiResult<PageResponse<TemplateResponse>> getTemplates(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @ModelAttribute TemplateSearchRequest request
    ) {
        PageResponse<TemplateResponse> response =
                templateService.getTemplates(principal.getId(), request.validateStatus(), request.getPage(), request.getSize());
        return ApiResult.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResult<TemplateResponse> getTemplateById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        TemplateResponse response = templateService.getTemplateById(id, principal.getId());
        return ApiResult.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createTemplate(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody TemplateCreateRequest templateCreateRequest
    ) {
        TemplateCreationResult result = templateService.createTemplate(
                principal.getId(),
                templateCreateRequest
        );

        return switch (result) {
            case TemplateCreationResult.Complete complete ->
                    ResponseEntity.status(HttpStatus.OK)
                            .body(ApiResult.ok(complete.template()));

            case TemplateCreationResult.Incomplete incomplete ->
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResult.ok(incomplete.partialTemplate()));
        };
    }

    @Operation(
            summary = "템플릿 승인 요청",
            description = "특정 템플릿에 대해 승인 요청을 보낸다."
    )
    @ApiErrorCodeExample(TemplateErrorCode.class)
    @PostMapping("/{id}/approve-request")
    public ApiResult<TemplateApproveResponse> approveTemplate(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        System.out.println(principal.getId());
        TemplateApproveResponse response = templateService.approveTemplate(id, principal.getId());
        return ApiResult.ok(response);
    }
}
