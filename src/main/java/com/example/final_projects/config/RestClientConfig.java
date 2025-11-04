package com.example.final_projects.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${rest.ai.base-url}")
    private String baseUrl;

    @Bean("aiRestClient")
    public RestClient aiRestClient(AiApiResponseErrorHandler aiApiResponseErrorHandler) {
        CloseableHttpClient httpClient = HttpClients.custom()
                .evictExpiredConnections()
                .evictIdleConnections(Timeout.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(aiApiResponseErrorHandler)
                .build();
    }
}
