package com.example.final_projects.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 모든 종류의 외부 API 에러를 담을 수 있는 재사용 가능한 '공용 예외' 클래스.
 * Java의 규칙('Throwable'을 상속하는 클래스는 제네릭일 수 없음)을 준수하기 위해
 * 클래스 자체는 non-generic으로, 내부에 Object 타입 필드와 제네릭 getter를 둠
 */
@Getter
public class RawExternalApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final Object rawErrorResponse;

    public RawExternalApiException(HttpStatus httpStatus, Object rawErrorResponse, String summaryMessage) {
        super(summaryMessage);
        this.httpStatus = httpStatus;
        this.rawErrorResponse = rawErrorResponse;
    }

    /**
     * 타입-안전한 제네릭 getter 메서드.
     * 서비스 계층에서 이 메서드를 통해 예외의 내용물을 형변환
     * @param type 변환하고자 하는 DTO의 클래스 타입
     * @return 타입이 변환된 DTO 객체
     */
    public <T> T getRawErrorResponse(Class<T> type) {
        if (type.isInstance(rawErrorResponse)) {
            return type.cast(rawErrorResponse);
        }
        // 타입 변환이 불가능한 경우
        throw new IllegalStateException("The rawErrorResponse is not of type " + type.getName() +
                ", but of type " + rawErrorResponse.getClass().getName());
    }
}
