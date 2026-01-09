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

import java.lang.invoke.MethodHandles;

@Service
public class ChatService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ChatClient chatClient;

    private final Evaluator evaluator;

    private final Resource systemPromptTemplate;

    private final Resource userPromptTemplate;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("ociGenAIRelevancyEvaluator") Evaluator evaluator,
            @Value("classpath:/prompts/chat/chatSystemPrompt.st") Resource systemPromptTemplate,
            @Value("classpath:/prompts/chat/chatUserPrompt.st") Resource userPromptTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.evaluator = evaluator;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = userPromptTemplate;
    }

    @Retryable(retryFor = AnswerNotRelevantException.class, maxAttempts = 2)
    public Answer askQuestion(Question question) {
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
            return new Answer("No answer");
        }

        logUsageMetadata(chatResponse);

        var answerText = chatResponse.getResult().getOutput().getText();

        evaluateRelevancy(question.question(), answerText);

        return new Answer(answerText);
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
        return new Answer("Can't find answer to your question.");
    }

    private void evaluateRelevancy(String questionText, String answerText) {
        if (!evaluator.evaluate(new EvaluationRequest(questionText, answerText)).isPass()) {
            throw new AnswerNotRelevantException(questionText, answerText);
        }
    }
}
