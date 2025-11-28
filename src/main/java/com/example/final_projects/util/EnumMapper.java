package com.example.final_projects.util;

import com.example.final_projects.exception.code.BaseErrorCode;
import java.util.Arrays;
import java.util.Optional;

public class EnumMapper {

    public static <T extends Enum<T> & BaseErrorCode> Optional<T> fromCode(Class<T> enumClass, String code) {
        if (code == null || enumClass == null) {
            return Optional.empty();
        }

        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.getErrorReason().getCode().equals(code))
                .findFirst();
    }

    public static <T extends Enum<T> & BaseErrorCode> T getFallback(Class<T> enumClass) {
        if (enumClass == null) return null;

        return Arrays.stream(enumClass.getEnumConstants())
                .filter(BaseErrorCode::isDefault)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        String.format("%s Enum에 'isDefault() == true'인 상수가 정의되지 않았습니다. " +
                                "AOP가 사용할 기본 에러 코드를 지정해주세요", enumClass.getName())
                ));
    }
}