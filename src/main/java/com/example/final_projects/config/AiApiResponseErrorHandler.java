package com.example.final_projects.config;

import com.example.final_projects.dto.template.AiApiResponse;
import com.example.final_projects.dto.template.AiErrorResponse;
import com.example.final_projects.exception.RawExternalApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiApiResponseErrorHandler extends DefaultResponseErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {

        HttpStatus status = (HttpStatus) response.getStatusCode();
        String responseBody = new String(response.getBody().readAllBytes());

        log.error("AI server returned an error. Status: {}, Raw Body: {}", status, responseBody);

        try {
            AiApiResponse<?> errorResponseWrapper = objectMapper.readValue(responseBody, new TypeReference<>() {});
            AiErrorResponse rawErrorResponse = errorResponseWrapper.error();

            if (rawErrorResponse != null) {
                throw new RawExternalApiException(status, rawErrorResponse, "AI server returned an error: " + rawErrorResponse.code());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI error JSON response body.", e);
        }

        AiErrorResponse fallbackError = new AiErrorResponse("PARSING_FAILED", "Failed to parse error response: " + responseBody, null);
        throw new RawExternalApiException(status, fallbackError, fallbackError.message());
    }
}
