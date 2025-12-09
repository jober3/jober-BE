package com.example.final_projects.aop;

import com.example.final_projects.dto.BaseErrorResponse;
import com.example.final_projects.entity.UserTemplateRequest;
import com.example.final_projects.exception.RawExternalApiException;
import com.example.final_projects.exception.code.BaseErrorCode;
import com.example.final_projects.service.FailureLogService;
import com.example.final_projects.service.UserTemplateRequestService;
import com.example.final_projects.util.EnumMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Constructor;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ExternalApiErrorHandlingAspect {

    private final FailureLogService failureLogService;
    private final UserTemplateRequestService userTemplateRequestService;
    private record RequestInfo(String clientIp, String userAgent) {}

    @Around(
            value = "@annotation(com.example.final_projects.aop.HandleExternalApiErrors) && args(userId, ..)",
            argNames = "joinPoint,userId"
    )
    public Object handleApiErrors(ProceedingJoinPoint joinPoint, Long userId) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (RawExternalApiException e) {
            log.warn("Handling RawExternalApiException via AOP for user {}", userId);

            RequestInfo requestInfo = extractRequestInfo();

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            HandleExternalApiErrors annotation = signature.getMethod().getAnnotation(HandleExternalApiErrors.class);

            BaseErrorResponse rawError = e.getRawErrorResponse(annotation.errorDtoClass());
            BaseErrorCode internalCode = resolveErrorCode(annotation.errorCodeClass(), rawError.getCode());

            handleFailureLog(userId, rawError, internalCode, e.getHttpStatus().value(), requestInfo);

            Constructor<? extends RuntimeException> constructor =
                    annotation.exceptionClass().getConstructor(BaseErrorCode.class, String.class);
            throw constructor.newInstance(internalCode, rawError.getMessage());
        }
    }

    private void handleFailureLog(Long userId, BaseErrorResponse rawError, BaseErrorCode internalCode, int httpStatus, RequestInfo requestInfo) {
        try {
            UserTemplateRequest userRequest = userTemplateRequestService.findLatestPendingRequestByUserId(userId);
            if (userRequest != null) {
                userTemplateRequestService.markAsFailed(userRequest.getId());

                failureLogService.saveFailureLog(
                        userRequest.getId(),
                        internalCode.getErrorReason().getCode(),
                        String.format("[Original Code: %s] %s", rawError.getCode(), rawError.getMessage()),
                        1,
                        requestInfo.userAgent,
                        requestInfo.clientIp,
                        httpStatus,
                        0L
                );
            } else {
                log.error("UserTemplateRequest not found for user {}", userId);
            }
        } catch (Exception ex) {
            log.error("Failed to save failure log", ex);
        }
    }

    private RequestInfo extractRequestInfo() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return new RequestInfo(getClientIp(request), request.getHeader("User-Agent"));
            }
        } catch (Exception ex) {
            log.debug("Not a web request context: {}", ex.getMessage());
        }
        return new RequestInfo("Unknown", "Unknown");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For에 여러 IP가 올 경우 첫 번째 것만 사용
        if (ip != null && ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        return ip;
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T> & BaseErrorCode> BaseErrorCode resolveErrorCode(Class<? extends BaseErrorCode> clazz, String code) {
        Class<T> enumClass = (Class<T>) clazz;

        return EnumMapper.fromCode(enumClass, code)
                .orElseGet(() -> EnumMapper.getFallback(enumClass));
    }
}
