package com.github.mstepan.demo_ai.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mstepan.demo_ai.web.Question;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "oci.genai.base.url"))
@SpringBootTest(properties = "oci.genai.base.url=${oci.genai.base.url}")
public class ChatServiceWireMockTest {

    @Value("classpath:/test-chat-response.json")
    Resource chatResponse;

    @Value("classpath:/test-relevance-evaluation-response.json")
    Resource relevanceEvaluationResponse;

    @Autowired ChatService chatService;

    @BeforeEach
    void setUp() throws IOException {
        var mapper = new ObjectMapper();

        var chatResponseNode =
                mapper.readTree(chatResponse.getContentAsString(StandardCharsets.UTF_8));

        var relevanceEvaluationResponseNode =
                mapper.readTree(
                        relevanceEvaluationResponse.getContentAsString(StandardCharsets.UTF_8));

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
    void askQuestion() {
        var answer = chatService.askQuestion(new Question("What is the capital of France?"));
        assertThat(answer).isNotNull();
        assertThat(answer.answer()).isEqualTo("The capital of France is Paris.");
    }
}
