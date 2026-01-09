package com.github.mstepan.demo_ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.github.mstepan.demo_ai.domain.Answer;
import com.github.mstepan.demo_ai.domain.Question;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.EnableRetry;

import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@SpringBootTest(classes = {ChatService.class, ChatServiceTest.MockConfig.class})
class ChatServiceTest {

    @TestConfiguration
    @EnableRetry
    static class MockConfig {
        @Bean
        @Primary
        ChatClient chatClient() {
            // Deep stubs to allow fluent mocking of prompt().system().user().call().chatResponse()
            // and .stream().content()
            return Mockito.mock(ChatClient.class, RETURNS_DEEP_STUBS);
        }

        @Bean
        @Primary
        ChatClient.Builder chatClientBuilder(ChatClient chatClient) {
            ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(chatClient);
            return builder;
        }

        @Bean
        @Primary
        @Qualifier("ociGenAIRelevancyEvaluator")
        Evaluator evaluator() {
            return Mockito.mock(Evaluator.class);
        }

        @Bean
        @Primary
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired ChatService chatService;

    @Autowired ChatClient chatClient;

    @Autowired
    @Qualifier("ociGenAIRelevancyEvaluator")
    Evaluator evaluator;

    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // Ensure clean stubbing for each test
        reset(chatClient, evaluator);
    }

    @Test
    @SuppressWarnings("unchecked")
    void askQuestionSuccessfulFlow() {
        // Arrange ChatClient chain to return a ChatResponse with desired text
        ChatResponse chatResponse = Mockito.mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);
        when(chatResponse.getResult().getOutput().getText())
                .thenReturn("The capital of France is Paris.");

        when(chatClient
                        .prompt()
                        .system(any(Resource.class))
                        .user(any(Consumer.class))
                        .call()
                        .chatResponse())
                .thenReturn(chatResponse);

        // Evaluator passes
        when(evaluator.evaluate(any(EvaluationRequest.class)))
                .thenReturn(new EvaluationResponse(true, 1.0f, "", Collections.emptyMap()));

        // Act
        Answer answer = chatService.askQuestion(new Question("What is the capital of France?"));

        // Assert
        assertThat(answer).isNotNull();
        assertThat(answer.answer()).isEqualTo("The capital of France is Paris.");

        // Metrics: success counter should be incremented
        var success = meterRegistry.find("app_oci_chat_success_total").counter();
        assertThat(success).isNotNull();
        assertThat(success.count()).isGreaterThan(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void askQuestionNonRelevantBothAttempts() {
        // Arrange ChatClient chain to return some ChatResponse with text
        ChatResponse chatResponse = Mockito.mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(chatResponse.getMetadata().getUsage()).thenReturn(null);
        when(chatResponse.getResult().getOutput().getText()).thenReturn("Some irrelevant answer.");

        when(chatClient
                        .prompt()
                        .system(any(Resource.class))
                        .user(any(Consumer.class))
                        .call()
                        .chatResponse())
                .thenReturn(chatResponse);

        // Evaluator fails both attempts to trigger @Recover path
        EvaluationResponse fail = new EvaluationResponse(false, 0.0f, "", Collections.emptyMap());
        when(evaluator.evaluate(any(EvaluationRequest.class))).thenReturn(fail, fail);

        // Act
        Answer answer = chatService.askQuestion(new Question("Please provide relevant info"));

        // Assert fallback answer from @Recover
        assertThat(answer).isNotNull();
        assertThat(answer.answer()).isEqualTo("Can't find answer to your question.");

        // Metrics: retries counter should be incremented (at least once)
        var retries =
                meterRegistry
                        .find("app_llm_retries_total")
                        .tags("reason", "AnswerNotRelevantException")
                        .counter();
        assertThat(retries).isNotNull();
        assertThat(retries.count()).isGreaterThan(0.0);

        // Failures counter should also be incremented due to thrown runtime path before recovery
        var failures = meterRegistry.find("app_oci_chat_failures_total").counter();
        assertThat(failures).isNotNull();
        assertThat(failures.count()).isGreaterThan(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void askQuestionNullChatResponse() {
        // Arrange ChatClient chain to return null ChatResponse
        when(chatClient
                        .prompt()
                        .system(any(Resource.class))
                        .user(any(Consumer.class))
                        .call()
                        .chatResponse())
                .thenReturn(null);

        // Act
        Answer answer = chatService.askQuestion(new Question("Any question"));

        // Assert
        assertThat(answer).isNotNull();
        assertThat(answer.answer()).isEqualTo("No answer");

        var failures = meterRegistry.find("app_oci_chat_failures_total").counter();
        assertThat(failures).isNotNull();
        assertThat(failures.count()).isGreaterThan(0.0);
    }

    @Test
    void askQuestionStreaming() {
        // Arrange streaming chain
        Flux<String> publisher = Flux.just("chunk-1", "chunk-2", "chunk-3");

        when(chatClient.prompt().system(any(Resource.class)).user(any(Consumer.class)).stream()
                        .content())
                .thenReturn(publisher);

        // Act
        List<String> chunks =
                chatService
                        .askQuestionStreaming(new Question("Stream please"))
                        .collectList()
                        .block();

        // Assert
        assertThat(chunks).isNotNull();
        assertThat(chunks).containsExactly("chunk-1", "chunk-2", "chunk-3");
    }
}
