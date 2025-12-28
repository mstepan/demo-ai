package com.github.mstepan.demo_ai.service;

import com.github.mstepan.demo_ai.evaluators.AnswerNotRelevantException;
import com.github.mstepan.demo_ai.web.Answer;
import com.github.mstepan.demo_ai.web.Question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class ChatService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ChatClient chatClient;

    private final Evaluator evaluator;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("ociGenAIRelevancyEvaluator") Evaluator evaluator) {
        this.chatClient = chatClientBuilder.build();
        this.evaluator = evaluator;
    }

    @Retryable(retryFor = AnswerNotRelevantException.class, maxAttempts = 2)
    public Answer askQuestion(Question question) {
        var answerText = chatClient.prompt().user(question.question()).call().content();

        evaluateRelevancy(question.question(), answerText);

        return new Answer(answerText);
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
