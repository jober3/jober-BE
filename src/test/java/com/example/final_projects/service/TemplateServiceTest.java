package com.example.final_projects.service;

import com.example.final_projects.dto.template.TemplateCreateRequest;
import com.example.final_projects.entity.*;
import com.example.final_projects.repository.TemplateHistoryRepository;
import com.example.final_projects.repository.TemplateRepository;
import com.example.final_projects.repository.UserTemplateRequestRepository;
import com.example.final_projects.support.MailService;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class TemplateServiceTest {

    @Autowired
    private TemplateService templateService;
    @Autowired
    private UserTemplateRequestRepository userTemplateRequestRepository;
    @Autowired
    private TemplateRepository templateRepository;
    @Autowired
    private TemplateHistoryRepository templateHistoryRepository;

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

    @Test
    @DisplayName("200 OK: 버튼과 변수가 포함된 템플릿 생성 성공 시, 모든 연관 데이터가 DB에 저장된다")
    @Sql("/data/test-data.sql")
    void createTemplate_200_OK_with_FullData_Success() {
        // given: 테스트 준비
        Long userId = 103L;
        TemplateCreateRequest createRequest = new TemplateCreateRequest("카페 이벤트");

        String mockAiResponseJson = """
        {
          "data": {
            "id": 1, "userId": 103, "categoryId": "004001", "title": "카페 이벤트",
            "content": "안녕하세요. #{카페이름}입니다.\\n\\n[광고] 카페 오픈 이벤트 안내\\n...",
            "imageUrl": null, "type": "MESSAGE", "isPublic": true,
            "buttons": [
              {
                "name": "확인하기", "linkMo": "https://mycafe.com/event", "linkPc": "https://mycafe.com/event",
                "linkAnd": null, "linkIos": null, "linkType": "WL", "ordering": 1
              }
            ],
            "variables": [
              {"id": 1, "variableKey": "이벤트기간", "placeholder": "#{이벤트기간}", "inputType": "TEXT"},
              {"id": 2, "variableKey": "이벤트URL", "placeholder": "#{이벤트URL}", "inputType": "TEXT"},
              {"id": 3, "variableKey": "전화번호", "placeholder": "#{전화번호}", "inputType": "TEXT"},
              {"id": 4, "variableKey": "수신거부번호", "placeholder": "#{수신거부번호}", "inputType": "TEXT"},
              {"id": 5, "variableKey": "카페이름", "placeholder": "#{카페이름}", "inputType": "TEXT"},
              {"id": 6, "variableKey": "이벤트내용", "placeholder": "#{이벤트내용}", "inputType": "TEXT"}
            ],
            "industries": [{"id": 4, "name": "공연/행사"}],
            "purposes": [{"id": 8, "name": "예약"}]
          }, "message": null, "error": null
        }
        """;

        wireMock.stubFor(WireMock.post("/ai/templates")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(mockAiResponseJson)));

        // when: 실제 서비스 로직 실행
        TemplateCreationResult result = templateService.createTemplate(userId, createRequest, "127.0.0.1", "test-agent");

        // then: 결과 검증
        assertInstanceOf(TemplateCreationResult.Complete.class, result);
        TemplateCreationResult.Complete completeResult = (TemplateCreationResult.Complete) result;
        assertThat(completeResult.template().getTitle()).isEqualTo("카페 이벤트");

        // 1. ID를 사용해 방금 생성된 Template 조회
        Template savedTemplate = templateRepository.findById(completeResult.template().getId()).orElseThrow();

        // 2. UserTemplateRequest의 상태가 COMPLETED으로 변경되었는지 DB에서 직접 확인
        UserTemplateRequest finalRequest = userTemplateRequestRepository
                .findById(savedTemplate.getUserTemplateRequest().getId())
                .orElseThrow();
        assertThat(finalRequest.getStatus()).isEqualTo(UserTemplateRequestStatus.COMPLETED);

        // 3. 저장된 Template 엔티티의 content를 확인
        assertThat(savedTemplate.getContent()).contains("#{카페이름}");

        // 4. OneToMany 관계인 자식 엔티티(Button)가 올바르게 저장되었는지 개수와 내용을 검증
        assertThat(savedTemplate.getButtons()).hasSize(1);
        assertThat(savedTemplate.getButtons().getFirst().getName()).isEqualTo("확인하기");
        assertThat(savedTemplate.getButtons().getFirst().getLinkPc()).isEqualTo("https://mycafe.com/event");

        // 5. 또 다른 자식 엔티티(Variable)의 개수를 검증
        assertThat(savedTemplate.getVariables()).hasSize(6);

        // 6. ManyToMany 관계인 Industry가 조인 테이블을 통해 올바르게 연결되었는지 검증
        assertThat(savedTemplate.getIndustries()).hasSize(1);
        assertThat(savedTemplate.getIndustries().iterator().next().getName()).isEqualTo("공연/행사");

        // 7. ManyToMany 관계인 Purpose가 올바르게 연결되었는지 검증
        assertThat(savedTemplate.getPurposes()).hasSize(1);
        assertThat(savedTemplate.getPurposes().iterator().next().getName()).isEqualTo("예약");

        // 8. TemplateHistory에 'CREATED' 상태의 기록이 저장되었는지 검증
        List<TemplateHistory> histories = templateHistoryRepository.findByTemplateId(savedTemplate.getId());
        assertThat(histories).hasSize(1);

        TemplateHistory savedHistory = histories.getFirst();
        assertThat(savedHistory.getTemplate().getId()).isEqualTo(savedTemplate.getId());
        assertThat(savedHistory.getStatus()).isEqualTo(TemplateStatus.CREATED);
    }

    @Test
    @DisplayName("202 Accepted: AI 템플릿 생성이 부분 성공하면, Template은 저장되지 않고 상태가 PENDING으로 유지된다")
    @Sql("/data/test-data.sql")
    void createTemplate_202_Accepted_PartialSuccess() {
        // given: 테스트 준비
        Long userId = 103L;
        TemplateCreateRequest createRequest = new TemplateCreateRequest("학원 안내");

        String mockAiResponseJson = """
        {
          "data": {
            "id": null, "userId": 103, "categoryId": "004001", "title": "학원 안내 (부분 완성)",
            "content": "안녕하세요, #{수강생 이름}님.\\n...",
            "type": "MESSAGE", "buttons": [],
            "variables": [
              {"variableKey": "수강료 금액", "placeholder": "#{수강료 금액}", "inputType": "TEXT", "value": ""},
              {"variableKey": "월", "placeholder": "#{월}", "inputType": "TEXT", "value": ""}
            ],
            "industries": [{"id": 1, "name": "학원"}],
            "purposes": [{"id": 11, "name": "기타"}]
          }, "message": null, "error": null
        }
        """;

        wireMock.stubFor(WireMock.post("/ai/templates")
                .willReturn(WireMock.aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(mockAiResponseJson)));

        // when: 실제 서비스 로직 실행
        TemplateCreationResult result = templateService.createTemplate(userId, createRequest, "127.0.0.1", "test-agent");

        // then: 결과 검증
        // 1. 반환된 결과가 'Incomplete' 타입인지 확인
        assertInstanceOf(TemplateCreationResult.Incomplete.class, result);
        TemplateCreationResult.Incomplete incompleteResult = (TemplateCreationResult.Incomplete) result;
        assertThat(incompleteResult.partialTemplate().title()).isEqualTo("학원 안내 (부분 완성)");
        assertThat(incompleteResult.partialTemplate().variables()).hasSize(2);

        // 2. UserTemplateRequest의 상태가 PENDING으로 유지되었는지 DB에서 직접 확인
        UserTemplateRequest finalRequest = userTemplateRequestRepository.findAll().getFirst();
        assertThat(finalRequest.getStatus()).isEqualTo(UserTemplateRequestStatus.PENDING);

        // 3. Template 테이블에는 데이터가 저장되지 않았는지 확인
        assertThat(templateRepository.count()).isZero();
    }
}
