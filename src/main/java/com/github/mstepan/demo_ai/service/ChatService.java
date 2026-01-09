package com.github.mstepan.demo_ai.service;

import com.github.mstepan.demo_ai.evaluators.AnswerNotRelevantException;
import com.github.mstepan.demo_ai.web.Answer;
import com.github.mstepan.demo_ai.web.Question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.lang.invoke.MethodHandles;

@Service
public class ChatService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ChatClient chatClient;

    private final Evaluator evaluator;

    private final Resource systemPromptTemplate;

    private final Resource userPromptTemplate;
    private final MeterRegistry meterRegistry;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("ociGenAIRelevancyEvaluator") Evaluator evaluator,
            @Value("classpath:/prompts/chat/chatSystemPrompt.st") Resource systemPromptTemplate,
            @Value("classpath:/prompts/chat/chatUserPrompt.st") Resource userPromptTemplate,
            MeterRegistry meterRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.evaluator = evaluator;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = userPromptTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Handles non-streaming chat requests.
     * Metrics (Prometheus/Micrometer):
     * - app_oci_chat_latency_seconds (Timer): end-to-end latency of upstream OCI call
     * - app_oci_chat_success_total (Counter): increments on successful answer after relevancy eval
     * - app_oci_chat_failures_total (Counter): increments on failures (exceptions or invalid responses)
     * Notes:
     * - Keep metric tag values low-cardinality to avoid cardinality explosions.
     */
    @Retryable(retryFor = AnswerNotRelevantException.class, maxAttempts = 2)
    public Answer askQuestion(Question question) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            var chatResponse =
                    chatClient
                            .prompt()
                            .system(systemPromptTemplate)
                            .user(
                                    userSpec ->
                                            userSpec.text(userPromptTemplate)
                                                    .param("question", question.question()))
                            .call()
                            .chatResponse();

            if (chatResponse == null) {
                meterRegistry.counter("app_oci_chat_failures_total").increment();
                return new Answer("No answer");
            }

            logUsageMetadata(chatResponse);

            var answerText = chatResponse.getResult().getOutput().getText();

            evaluateRelevancy(question.question(), answerText);

            meterRegistry.counter("app_oci_chat_success_total").increment();
            return new Answer(answerText);
        } catch (RuntimeException ex) {
            meterRegistry.counter("app_oci_chat_failures_total").increment();
            throw ex;
        } finally {
            sample.stop(Timer.builder("app_oci_chat_latency_seconds").register(meterRegistry));
        }
    }

    private static void logUsageMetadata(ChatResponse chatResponse) {
        if (chatResponse.getMetadata().getUsage() != null) {
            var usage = chatResponse.getMetadata().getUsage();

            if (usage instanceof EmptyUsage) {
                LOGGER.info("Token usage: EMPTY");
                return;
            }

            LOGGER.info(
                    "Token usage: prompt = {}, generation = {}, total = {}",
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
        }
    }

    public Flux<String> askQuestionStreaming(Question question) {
        return chatClient
                .prompt()
                .system(systemPromptTemplate)
                .user(
                        userSpec ->
                                userSpec.text(userPromptTemplate)
                                        .param("question", question.question()))
                .stream()
                .content();
    }

    @Recover
    public Answer recover(AnswerNotRelevantException ex) {
        // Track recoveries/retry occurrences with bounded tag values
        meterRegistry
                .counter("app_llm_retries_total", "reason", "AnswerNotRelevantException")
                .increment();
        return new Answer("Can't find answer to your question.");
    }

    private void evaluateRelevancy(String questionText, String answerText) {
        boolean pass = evaluator.evaluate(new EvaluationRequest(questionText, answerText)).isPass();
        // Bounded cardinality tag: outcome in {"yes","no"}
        meterRegistry
                .counter("app_evaluator_relevancy_total", "outcome", pass ? "yes" : "no")
                .increment();
        if (!pass) {
            // Count a retry-triggering failure attempt
            meterRegistry
                    .counter("app_llm_retries_total", "reason", "AnswerNotRelevantException")
                    .increment();
            throw new AnswerNotRelevantException(questionText, answerText);
        }
    }
}
