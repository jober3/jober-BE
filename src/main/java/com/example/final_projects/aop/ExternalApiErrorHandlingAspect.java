package com.example.final_projects.aop;

import com.example.final_projects.dto.BaseErrorResponse;
import com.example.final_projects.entity.UserTemplateRequest;
import com.example.final_projects.exception.AiException;
import com.example.final_projects.exception.RawExternalApiException;
import com.example.final_projects.exception.code.BaseErrorCode;
import com.example.final_projects.service.FailureLogService;
import com.example.final_projects.service.UserTemplateRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ExternalApiErrorHandlingAspect {

    private final FailureLogService failureLogService;
    private final UserTemplateRequestService userTemplateRequestService;

    @Around(
            value = "@annotation(com.example.final_projects.aop.HandleExternalApiErrors) && args(userId, .., clientIp, userAgent)",
            argNames = "joinPoint,userId,clientIp,userAgent"
    )
    public Object handleApiErrors(ProceedingJoinPoint joinPoint, Long userId, String clientIp, String userAgent) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (RawExternalApiException e) {
            log.warn("Handling RawExternalApiException via AOP for user {}", userId);

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            HandleExternalApiErrors annotation = signature.getMethod().getAnnotation(HandleExternalApiErrors.class);

            Class<? extends BaseErrorCode> errorCodeClass = annotation.errorCodeClass();
            Class<? extends BaseErrorResponse> errorDtoClass = annotation.errorDtoClass();

            BaseErrorResponse rawError = e.getRawErrorResponse(errorDtoClass);
            String rawErrorCodeString = rawError.getCode();
            String originalMessage = rawError.getMessage();

            Method fromCodeMethod = errorCodeClass.getDeclaredMethod("fromCode", String.class);
            BaseErrorCode internalCode = (BaseErrorCode) fromCodeMethod.invoke(null, rawErrorCodeString);

            UserTemplateRequest userRequest = userTemplateRequestService.findLatestPendingRequestByUserId(userId);

            if (userRequest != null) {
                userTemplateRequestService.markAsFailed(userRequest.getId());
                failureLogService.saveFailureLog(
                        userRequest.getId(),
                        internalCode.getErrorReason().getCode(),
                        String.format("[Original Code: %s] %s", rawErrorCodeString, originalMessage),
                        1, userAgent, clientIp, e.getHttpStatus().value(), 0L
                );
            } else {
                log.error("Could not find UserTemplateRequest for user {} to log failure.", userId);
            }

            throw new AiException(internalCode, originalMessage);
        }
    }
}
