package com.example.final_projects.exception;

import com.example.final_projects.dto.ApiResult;
import com.example.final_projects.dto.ErrorResponse;
import com.example.final_projects.exception.code.AiErrorCode;
import com.example.final_projects.exception.code.BaseErrorCode;
import com.example.final_projects.exception.user.UserErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.final_projects.exception.user.UserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private <T> ResponseEntity<ApiResult<T>> buildErrorResponse(HttpStatus status, String code, String message) {
        return ResponseEntity
                .status(status)
                .body(ApiResult.<T>builder()
                        .data(null)
                        .error(ErrorResponse.of(code, message))
                        .build()
                );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class, NullPointerException.class})
    public ResponseEntity<ApiResult<Object>> handleServerErrors(RuntimeException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Object>> handleGeneral(Exception ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", ex.getMessage());
    }

    @ExceptionHandler(TemplateException.class)
    public ResponseEntity<ApiResult<Object>> handleTemplateException(TemplateException ex) {
        BaseErrorCode errorCode = ex.getErrorCode();
        return buildErrorResponse(
                HttpStatus.valueOf(errorCode.getErrorReason().getStatus()),
                errorCode.getErrorReason().getCode(),
                errorCode.getErrorReason().getMessage()
        );
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiResult<Object>> handleUser(UserException ex, HttpServletRequest req){
        UserErrorCode ec = ex.getErrorCode();
        String msg = (ex.getMessage() != null) ? ex.getMessage() : ec.getDefaultMessage();

        if(ex.getDetails() != null) {
            Object safe = sanitizeDetails(ex.getDetails());
            log.warn("UserException path={} code={} status={} details={}",
                    req.getRequestURI(), ec.code(), ec.getStatus().value(), safe);
        }else{
            log.warn("UserException path={} code={} status={}",
                    req.getRequestURI(), ec.code(), ec.getStatus().value());
        }
        return buildErrorResponse(ec.getStatus(), ec.code(), msg);
    }
    private Object sanitizeDetails(Object details) {
        if(details == null) return null;
        if(details instanceof java.util.Map){
            Map<?, ?> src = (Map<?, ?>) details;
            Map<Object, Object> masked = new java.util.HashMap<>();
            int i = 0;
            for(Map.Entry<?, ?> e : src.entrySet()){
                if (i++ > 20){ masked.put("_more", "..."); break;}
                String key = String.valueOf(e.getKey()).toLowerCase();
                if(key.contains("password") || key.contains("token") || key.contains("otp") || key.contains("secret")){
                    masked.put(e.getKey(), "[REDACTED]");
                } else {
                    masked.put(e.getKey(), e.getValue());
                }
            }
            return masked;
        }
        if(details instanceof CharSequence){
            String s = details.toString();
            s = s.replaceAll("([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)@([A-Za-z0-9.-]+)", "$1***@$3");
            if(s.length() > 500) s = s.substring(0, 500) + "...";
            return s;
        }
        return details;
    }

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiResult<Object>> handleAiException(AiException ex) {
        BaseErrorCode errorCode = ex.getErrorCode();
        String responseMessage;

        // 내부 통제가 필요한 특정 에러 코드인지 확인
        if (errorCode == AiErrorCode.UNEXPECTED_AI_RESPONSE ||
                errorCode == AiErrorCode.AI_REQUEST_FAILED ||
                errorCode == AiErrorCode.SERVICE_UNAVAILABLE) {
            responseMessage = errorCode.getErrorReason().getMessage();
        } else {
            // 그 외의 모든 경우에는 AI 서버가 보내준 원본 메시지를 그대로 사용
            responseMessage = ex.getMessage();
        }

        return buildErrorResponse(
                HttpStatus.valueOf(errorCode.getErrorReason().getStatus()),
                errorCode.getErrorReason().getCode(),
                responseMessage
        );
    }
}
