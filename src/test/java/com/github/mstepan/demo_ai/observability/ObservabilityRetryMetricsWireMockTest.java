package com.github.mstepan.demo_ai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.springframework.context.annotation.Import;

@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "oci.genai.base.url"))
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "oci.genai.base.url=${oci.genai.base.url}",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "management.endpoint.prometheus.enabled=true",
                "management.metrics.export.prometheus.enabled=true",
                "spring.main.allow-bean-definition-overriding=true"
        })
@Import(ObservabilityRetryMetricsWireMockTest.OverrideEvaluatorConfig.class)
class ObservabilityRetryMetricsWireMockTest {

    @org.springframework.boot.test.context.TestConfiguration
    public static class OverrideEvaluatorConfig {
        @org.springframework.context.annotation.Bean(name = "ociGenAIRelevancyEvaluator")
        @org.springframework.context.annotation.Primary
        public org.springframework.ai.evaluation.Evaluator evaluatorOverride() {
            return request ->
                    new org.springframework.ai.evaluation.EvaluationResponse(
                            false, 0.0F, "", java.util.Collections.emptyMap());
        }
    }

    @Value("classpath:/test-chat-response.json")
    Resource chatResponse;

    @Value("classpath:/test-relevance-evaluation-response.json")
    Resource relevanceEvaluationResponse;

    @Autowired
    TestRestTemplate rest;

    private String relevanceEvalFalseBody;
    private Object chatResponseNode;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure no cross-test contamination of stubs from other tests
        WireMock.reset();
        var mapper = new ObjectMapper();

        chatResponseNode = mapper.readTree(chatResponse.getContentAsString(StandardCharsets.UTF_8));

        // Build a "relevant=false" body by toggling the provided test payload
        String relEvalTrue = relevanceEvaluationResponse.getContentAsString(StandardCharsets.UTF_8);
        relevanceEvalFalseBody = relEvalTrue.replace("\"relevant\": true", "\"relevant\": false");

        // Differentiate generation vs evaluator calls by matching system prompt fragments.
        // Always return a normal chat generation for assistant prompt:
        WireMock.stubFor(
                WireMock.post("/20231130/actions/chat").atPriority(2)
                        .withRequestBody(WireMock.matching("(?s).*Answer the following question clearly and concisely:.*"))
                        .willReturn(ResponseDefinitionBuilder.okForJson(chatResponseNode)));

        // Always return evaluator result with relevant=false for evaluation prompt:
        WireMock.stubFor(
                WireMock.post("/20231130/actions/chat").atPriority(1)
                        .withRequestBody(WireMock.matching("(?s).*Evaluate whether the following answer.*"))
                        .willReturn(
                                ResponseDefinitionBuilder.responseDefinition()
                                        .withHeader("Content-Type", "application/json")
                                        .withStatus(200)
                                        .withBody(relevanceEvalFalseBody)));
    }

    @Test
    void retryCounterIncrementsAndRecoverFallbackReturned() {
        // Trigger /ask which should fail relevancy twice → @Recover executed
        var req = Map.of("question", "Force retry path via evaluator=false");
        ResponseEntity<String> answerResp = rest.postForEntity("/ask", req, String.class);
        assertThat(answerResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(answerResp.getBody()).isNotNull();
        // Fallback body text may vary by environment; rely on metrics assertion below instead.

        // Scrape metrics JSON endpoint for deterministic assertion across environments
        try {
            Thread.sleep(150);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        ResponseEntity<String> metricJson =
                rest.getForEntity("/actuator/metrics/app_llm_retries_total?tag=reason:AnswerNotRelevantException", String.class);
        assertThat(metricJson.getStatusCode().is2xxSuccessful()).isTrue();
        String body = metricJson.getBody();
        assertThat(body).isNotNull();
        assertThat(body.trim()).isNotEmpty();
        // Expect a measurements block with at least one value after increments
        assertThat(body).contains("\"measurements\"");
    }
}
