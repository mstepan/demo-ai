package com.github.mstepan.demo_ai.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mstepan.demo_ai.web.Question;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.io.IOException;
import java.nio.charset.Charset;

@EnableWireMock(@ConfigureWireMock(baseUrlProperties = "oci.genai.base.url"))
@SpringBootTest(properties = "oci.genai.base.url=${oci.genai.base.url}")
public class ChatServiceWireMockTest {

    @Value("classpath:/test-oci-genai-response.json")
    Resource responseResource;

    @Autowired ChatClient.Builder chatClientBuilder;

    @BeforeEach
    void setUp() throws IOException {
        var predefinedResponse = responseResource.getContentAsString(Charset.defaultCharset());
        var mapper = new ObjectMapper();
        var responseNode = mapper.readTree(predefinedResponse);
        WireMock.stubFor(
                WireMock.post("/20231130/actions/chat")
                        .willReturn(ResponseDefinitionBuilder.okForJson(responseNode)));
    }

    @Test
    void askQuestion() {
        var chatService = new ChatService(chatClientBuilder);
        var answer = chatService.askQuestion(new Question("What is the capital of France?"));
        assertThat(answer).isNotNull();
        assertThat(answer.answer()).isEqualTo("Paris");
    }
}
