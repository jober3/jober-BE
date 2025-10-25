package com.example.final_projects.service;

import com.example.final_projects.dto.template.AiApiResponse;
import com.example.final_projects.dto.template.AiTemplateRequest;
import com.example.final_projects.dto.template.AiTemplateResponse;
import com.example.final_projects.entity.UserTemplateRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiApiClient {

    private final RestClient restClient;

    public AiApiClient(@Qualifier("aiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ResponseEntity<AiApiResponse<AiTemplateResponse>> createTemplate(UserTemplateRequest userTemplateRequest) {
        return restClient.post()
                .uri("/ai/templates")
                .body(new AiTemplateRequest(userTemplateRequest.getUserId(), userTemplateRequest.getRequestContent()))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {});
    }
}
