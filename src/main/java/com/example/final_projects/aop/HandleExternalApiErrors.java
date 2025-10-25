package com.example.final_projects.aop;

import com.example.final_projects.dto.BaseErrorResponse;
import com.example.final_projects.exception.code.BaseErrorCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleExternalApiErrors {
    Class<? extends BaseErrorCode> errorCodeClass();
    Class<? extends BaseErrorResponse> errorDtoClass();
}
