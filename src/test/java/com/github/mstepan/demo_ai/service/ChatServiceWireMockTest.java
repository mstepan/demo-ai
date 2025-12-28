package com.github.mstepan.demo_ai.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mstepan.demo_ai.web.Question;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "oci.genai.base.url"))
@SpringBootTest(properties = "oci.genai.base.url=${oci.genai.base.url}")
public class ChatServiceWireMockTest {

    @Value("classpath:/test-oci-genai-response.json")
    Resource responseResource;

    @Autowired ChatClient.Builder chatClientBuilder;

    @Autowired
    @Qualifier("ociGenAIRelevancyEvaluator")
    Evaluator relevancyEvaluator;

    @Value("classpath:/prompts/chat/chatSystemPrompt.st")
    Resource systemPromptTemplate;

    @Value("classpath:/prompts/chat/chatUserPrompt.st")
    Resource userPromptTemplate;

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        @Qualifier("ociGenAIRelevancyEvaluator")
        public Evaluator mockEvaluator() {
            return Mockito.mock(Evaluator.class);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        Mockito.reset(relevancyEvaluator);
        var predefinedResponse = responseResource.getContentAsString(Charset.defaultCharset());
        var mapper = new ObjectMapper();
        var responseNode = mapper.readTree(predefinedResponse);
        WireMock.stubFor(
                WireMock.post("/20231130/actions/chat")
                        .willReturn(ResponseDefinitionBuilder.okForJson(responseNode)));
    }

    @Test
    void askQuestion() {
        when(relevancyEvaluator.evaluate(any()))
                .thenReturn(new EvaluationResponse(true, 1.0F, "", Map.of()));

        var chatService =
                new ChatService(
                        chatClientBuilder,
                        relevancyEvaluator,
                        systemPromptTemplate,
                        userPromptTemplate);
        var answer = chatService.askQuestion(new Question("What is the capital of France?"));
        assertThat(answer).isNotNull();
        assertThat(answer.answer()).isEqualTo("The capital of France is Paris.");

        verify(relevancyEvaluator, times(1)).evaluate(any());
    }
}
