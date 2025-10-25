package com.example.final_projects.dto.template;

import com.example.final_projects.dto.BaseErrorResponse;

public record AiErrorResponse(
        String code,
        String message,
        String timestamp
) implements BaseErrorResponse {

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
