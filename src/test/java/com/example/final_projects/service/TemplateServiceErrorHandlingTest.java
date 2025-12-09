package com.example.final_projects.service;

import com.example.final_projects.entity.UserTemplateRequest;
import com.example.final_projects.entity.UserTemplateRequestFailureLog;
import com.example.final_projects.entity.UserTemplateRequestStatus;
import com.example.final_projects.repository.UserTemplateRequestFailureLogRepository;
import com.example.final_projects.repository.UserTemplateRequestRepository;
import com.example.final_projects.security.WithMockCustomUser;
import com.example.final_projects.support.MailService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TemplateServiceErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserTemplateRequestRepository userTemplateRequestRepository;

    @Autowired
    private UserTemplateRequestFailureLogRepository failureLogRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MailService mailService() {
            return Mockito.mock(MailService.class);
        }
    }

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("rest.ai.base-url", wireMock::baseUrl);
    }

    @AfterEach
    void tearDown() {
        failureLogRepository.deleteAll();
        userTemplateRequestRepository.deleteAll();
    }

    @Test
    @WithMockCustomUser(id = 104L, roles = "USER")
    @DisplayName("예측 가능한 API 에러(POLICY_VIOLATION) 발생 시, 프론트엔드에는 원본 메시지를, DB에는 상세 로그를 기록한다")
    void when_predictable_error_occurs_then_return_original_message_and_log_details() throws Exception {
        // given: AI 서버가 우리가 아는 "POLICY_VIOLATION" 에러를 반환하도록 Mocking
        String originalMessage = "템플릿에 허용되지 않는 단어('무료 증정')가 포함되어 있습니다.";
        wireMock.stubFor(WireMock.post("/ai/templates")
                .willReturn(WireMock.badRequest()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": {\"code\": \"POLICY_VIOLATION\", \"message\": \"" + originalMessage + "\"}}")));
        String testIp = "123.45.67.89";
        String testUa = "Mozilla/5.0 (IntegrationTest)";

        // when: 컨트롤러의 템플릿 생성 API를 호출
        ResultActions resultActions = mockMvc.perform(post("/api/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestContent\": \"무료 증정 이벤트\"}")
                .header("X-Forwarded-For", testIp)
                .header("User-Agent", testUa)
                .with(csrf()));

        // then (1): 프론트엔드에 전달되는 응답 검증
        // GlobalExceptionHandler의 else 블록이 실행되어, 원본 메시지가 그대로 전달되어야 함
        resultActions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("POLICY_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value(originalMessage));

        // then (2): UserTemplateRequest 상태 검증 (실패로 변경되었는지)
        List<UserTemplateRequest> requests = userTemplateRequestRepository.findAll();
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().getStatus()).isEqualTo(UserTemplateRequestStatus.FAILED);

        // then (3): 데이터베이스에 기록된 FailureLog 검증
        List<UserTemplateRequestFailureLog> logs = failureLogRepository.findAll();
        assertThat(logs).hasSize(1);
        UserTemplateRequestFailureLog savedLog = logs.getFirst();
        assertThat(savedLog.getErrorCode()).isEqualTo("POLICY_VIOLATION");
        assertThat(savedLog.getErrorDetail()).isEqualTo("[Original Code: POLICY_VIOLATION] " + originalMessage);
        assertThat(savedLog.getClientIp()).isEqualTo(testIp);
        assertThat(savedLog.getUserAgent()).isEqualTo(testUa);
        assertThat(savedLog.getHttpStatusCode()).isEqualTo(400);
    }

    @Test
    @WithMockCustomUser(id = 105L, roles = "USER")
    @DisplayName("예측 불가능한 API 에러(SERVER_ON_FIRE) 발생 시, 프론트엔드에는 표준 메시지를, DB에는 상세 로그를 기록한다")
    void when_unexpected_error_occurs_then_return_standard_message_and_log_details() throws Exception {
        // given: AI 서버가 우리가 모르는 "SERVER_ON_FIRE" 에러를 반환하도록 Mocking
        String originalMessage = "Help! The server is melting!";
        wireMock.stubFor(WireMock.post("/ai/templates")
                .willReturn(WireMock.serverError()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": {\"code\": \"SERVER_ON_FIRE\", \"message\": \"" + originalMessage + "\"}}")));

        // when: 컨트롤러의 템플릿 생성 API를 호출
        ResultActions resultActions = mockMvc.perform(post("/api/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestContent\": \"trigger unexpected error\"}")
                .with(csrf()));

        // then (1): 프론트엔드에 전달되는 응답 검증
        // GlobalExceptionHandler의 if 블록이 실행되어, 안전한 표준 메시지가 전달되어야 함
        resultActions.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("UNEXPECTED_AI_RESPONSE"))
                .andExpect(jsonPath("$.error.message").value("AI 서버로부터 예상치 못한 응답을 받았습니다.")); // 표준 메시지 검증

        // then (2): UserTemplateRequest 상태 검증 (실패로 변경되었는지)
        List<UserTemplateRequest> requests = userTemplateRequestRepository.findAll();
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().getStatus()).isEqualTo(UserTemplateRequestStatus.FAILED);

        // then (3): 데이터베이스에 기록된 FailureLog 검증
        List<UserTemplateRequestFailureLog> logs = failureLogRepository.findAll();
        assertThat(logs).hasSize(1);
        UserTemplateRequestFailureLog savedLog = logs.getFirst();
        assertThat(savedLog.getErrorCode()).isEqualTo("UNEXPECTED_AI_RESPONSE");
        assertThat(savedLog.getErrorDetail()).isEqualTo("[Original Code: SERVER_ON_FIRE] " + originalMessage);
        assertThat(savedLog.getHttpStatusCode()).isEqualTo(500);
    }

    @Test
    @WithMockCustomUser(id = 106L, roles = "USER")
    @DisplayName("API 응답 파싱 에러 발생 시, 프론트엔드에는 표준 메시지를, DB에는 원본 응답 로그를 기록한다")
    void when_parsing_error_occurs_then_return_standard_message_and_log_raw_body() throws Exception {
        // given: AI 서버가 JSON 형식이 아닌, 일반 텍스트 에러 메시지를 반환하도록 Mocking
        String rawErrorBody = "<h1>500 Internal Server Error</h1><p>Something went wrong on the AI server.</p>";
        wireMock.stubFor(WireMock.post("/ai/templates")
                .willReturn(WireMock.serverError()
                        .withStatus(500)
                        .withHeader("Content-Type", "text/html")
                        .withBody(rawErrorBody)));

        // when: 컨트롤러의 템플릿 생성 API를 호출
        ResultActions resultActions = mockMvc.perform(post("/api/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestContent\": \"trigger parsing error\"}")
                .with(csrf()));

        // then (1): 프론트엔드에 전달되는 응답 검증
        // ErrorHandler의 catch 블록 -> PARSING_FAILED -> Aspect -> UNEXPECTED_AI_RESPONSE -> GlobalExceptionHandler의 if 블록 실행
        resultActions.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("UNEXPECTED_AI_RESPONSE"))
                .andExpect(jsonPath("$.error.message").value("AI 서버로부터 예상치 못한 응답을 받았습니다."));

        // then (2): UserTemplateRequest 상태 검증 (실패로 변경되었는지)
        List<UserTemplateRequest> requests = userTemplateRequestRepository.findAll();
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().getStatus()).isEqualTo(UserTemplateRequestStatus.FAILED);

        // then (3): 데이터베이스에 기록된 FailureLog 검증
        List<UserTemplateRequestFailureLog> logs = failureLogRepository.findAll();
        assertThat(logs).hasSize(1);
        UserTemplateRequestFailureLog savedLog = logs.getFirst();
        assertThat(savedLog.getErrorCode()).isEqualTo("UNEXPECTED_AI_RESPONSE");
        assertThat(savedLog.getErrorDetail()).isEqualTo("[Original Code: PARSING_FAILED] Failed to parse error response: " + rawErrorBody);
        assertThat(savedLog.getHttpStatusCode()).isEqualTo(500);
    }
}
