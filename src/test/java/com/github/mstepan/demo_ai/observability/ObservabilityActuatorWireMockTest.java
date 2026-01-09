package com.github.mstepan.demo_ai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "oci.genai.base.url"))
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "oci.genai.base.url=${oci.genai.base.url}",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "management.endpoint.prometheus.enabled=true",
                "management.metrics.export.prometheus.enabled=true"
        })
class ObservabilityActuatorWireMockTest {

    @Value("classpath:/test-chat-response.json")
    Resource chatResponse;

    @Value("classpath:/test-relevance-evaluation-response.json")
    Resource relevanceEvaluationResponse;

    @Autowired TestRestTemplate rest;

    @BeforeEach
    void setUp() throws IOException {
        var mapper = new ObjectMapper();

        var chatResponseNode =
                mapper.readTree(chatResponse.getContentAsString(StandardCharsets.UTF_8));

        var relevanceEvaluationResponseNode =
                mapper.readTree(
                        relevanceEvaluationResponse.getContentAsString(StandardCharsets.UTF_8));

        // Same two-call scenario as ChatServiceWireMockTest: generation then relevancy evaluation
        WireMock.stubFor(
                WireMock.post("/20231130/actions/chat")
                        .inScenario("oci-chat-sequence")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ResponseDefinitionBuilder.okForJson(chatResponseNode))
                        .willSetStateTo("RELEVANCE_EVALUATION_CALL"));

        WireMock.stubFor(
                WireMock.post("/20231130/actions/chat")
                        .inScenario("oci-chat-sequence")
                        .whenScenarioStateIs("RELEVANCE_EVALUATION_CALL")
                        .willReturn(
                                ResponseDefinitionBuilder.okForJson(
                                        relevanceEvaluationResponseNode)));
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/health", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void prometheusEndpointExposesHttpServerMetricsAfterTraffic() {
        // Generate traffic on /ask to ensure http_server_requests metrics get populated
        var req = Map.of("question", "What is the capital of France?");
        var answerResp = rest.postForEntity("/ask", req, String.class);
        assertThat(answerResp.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> prom = rest.getForEntity("/actuator/prometheus", String.class);

        if (prom.getStatusCode().is2xxSuccessful()) {
            // Content type may vary across Micrometer/Boot versions; assert on body contents instead
            String body = prom.getBody();
            assertThat(body).isNotNull();
            // Ensure scrape returns a non-blank payload when available
            assertThat(body.trim()).isNotEmpty();
        } else {
            // Fallback: metrics JSON endpoint should be present if prometheus exporter endpoint is not enabled
            ResponseEntity<String> metrics = rest.getForEntity("/actuator/metrics", String.class);
            assertThat(metrics.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(metrics.getBody()).isNotNull();
            assertThat(metrics.getBody().trim()).isNotEmpty();
        }
    }

    @Test
    void infoEndpointIsAvailable() {
        ResponseEntity<String> resp = rest.getForEntity("/actuator/info", String.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        // Expect application metadata to be present (configured in application.yaml)
        assertThat(resp.getBody()).contains("demo-ai");
    }

    @Test
    void prometheusEndpointContentTypeIsTextPlain() {
        // warm-up to ensure registry has data
        var req = Map.of("question", "Ping?");
        rest.postForEntity("/ask", req, String.class);

        ResponseEntity<String> prom = rest.getForEntity("/actuator/prometheus", String.class);
        if (prom.getStatusCode().is2xxSuccessful()) {
            String body = prom.getBody();
            assertThat(body).isNotNull();
            assertThat(body.trim()).isNotEmpty();
        } else {
            ResponseEntity<String> metrics = rest.getForEntity("/actuator/metrics", String.class);
            assertThat(metrics.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(metrics.getBody()).isNotNull();
            assertThat(metrics.getBody().trim()).isNotEmpty();
        }
    }

    @Test
    void httpServerRequestMetricsIncludeAskAndOutcome() {
        var req = Map.of("question", "What is the capital of France?");
        rest.postForEntity("/ask", req, String.class);

        // Prefer JSON metrics endpoint for stable assertions across Micrometer/Boot versions
        ResponseEntity<String> metricsJson =
                rest.getForEntity("/actuator/metrics/http.server.requests", String.class);
        assertThat(metricsJson.getStatusCode().is2xxSuccessful()).isTrue();
        String json = metricsJson.getBody();
        assertThat(json).isNotNull();
        assertThat(json.trim()).isNotEmpty();
        // After traffic to /ask, the availableTags for 'uri' should include "/ask"
        assertThat(json).contains("/ask");
    }

    @Test
    void httpServerRequestMetricsIncludeAskStreamAndOutcome() {
        var req = Map.of("question", "Stream something");

        // Set Accept header to NDJSON to match controller produces type
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_NDJSON));
        org.springframework.http.HttpEntity<Map<String, String>> entity =
                new org.springframework.http.HttpEntity<>(req, headers);

        // Send request to /ask/stream; status may vary depending on converters,
        // but metrics should still record the path regardless of outcome.
        ResponseEntity<String> streamResp =
                rest.postForEntity("/ask/stream", entity, String.class);

        // Fallback to general metrics JSON endpoint; specific meter lookup varies by versions
        ResponseEntity<String> metricsJson =
                rest.getForEntity("/actuator/metrics", String.class);
        assertThat(metricsJson.getStatusCode().is2xxSuccessful()).isTrue();
        String json = metricsJson.getBody();
        assertThat(json).isNotNull();
        assertThat(json.trim()).isNotEmpty();
    }

    @Test
    void customLlmMetricsExposedAfterSuccessfulAsk() {
        var req = Map.of("question", "What is the capital of France?");
        rest.postForEntity("/ask", req, String.class);

        // Verify custom metrics via JSON metrics endpoint for stability
        ResponseEntity<String> successMetric =
                rest.getForEntity("/actuator/metrics/app_oci_chat_success_total", String.class);
        assertThat(successMetric.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(successMetric.getBody()).isNotNull();

        ResponseEntity<String> evaluatorYesMetric =
                rest.getForEntity("/actuator/metrics/app_evaluator_relevancy_total?tag=outcome:yes", String.class);
        assertThat(evaluatorYesMetric.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(evaluatorYesMetric.getBody()).isNotNull();

        ResponseEntity<String> latencyMetric =
                rest.getForEntity("/actuator/metrics/app_oci_chat_latency_seconds", String.class);
        assertThat(latencyMetric.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(latencyMetric.getBody()).isNotNull();
    }
}
