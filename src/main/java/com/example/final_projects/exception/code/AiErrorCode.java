package com.example.final_projects.exception.code;

import com.example.final_projects.exception.ErrorReason;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum AiErrorCode implements BaseErrorCode {
    // AI 서버와 1:1 매핑
    PROFANITY_DETECTED(HttpStatus.BAD_REQUEST.value(), "부적절한 언어가 감지되었습니다."),
    POLICY_VIOLATION(HttpStatus.BAD_REQUEST.value(), "정책에 위반되는 내용이 포함되었습니다."),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY.value(), "입력값 검증에 실패했습니다."),
    PROCESSING_TIMEOUT(HttpStatus.REQUEST_TIMEOUT.value(), "AI 서버 처리 시간이 초과되었습니다."),
    API_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS.value(),"API 할당량을 초과했습니다."),
    TEMPLATE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR.value(), "템플릿 생성에 실패했습니다."),

    // Spring Boot 서버 내부 정의
    AI_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR.value(), "AI 서버 요청에 실패했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE.value(), "AI 서비스를 현재 사용할 수 없습니다."),
    UNEXPECTED_AI_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR.value(), "AI 서버로부터 예상치 못한 응답을 받았습니다.");

    private final ErrorReason errorReason;

    AiErrorCode(int status, String message) {this.errorReason = new ErrorReason(status, this.name(), message);}

    private static final Map<String, AiErrorCode> codeMap = Arrays.stream(values())
            .filter(code -> !code.isInternal()) // 내부 정의 코드는 제외
            .collect(Collectors.toMap(
                    code -> code.getErrorReason().getCode(),
                    Function.identity()
            ));

    public static AiErrorCode fromCode(String code) {
        return codeMap.getOrDefault(code, UNEXPECTED_AI_RESPONSE);
    }

    private boolean isInternal() {
        return this == AI_REQUEST_FAILED || this == SERVICE_UNAVAILABLE || this == UNEXPECTED_AI_RESPONSE;
    }

    @Override
    public ErrorReason getErrorReason() { return errorReason; }
}
