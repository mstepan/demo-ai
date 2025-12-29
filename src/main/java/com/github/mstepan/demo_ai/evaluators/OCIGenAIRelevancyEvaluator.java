package com.github.mstepan.demo_ai.evaluators;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Evaluator that uses an OCI GenAI-backed ChatClient to decide whether a candidate answer is
 * relevant to and correctly addresses the provided question. The model is instructed to reply with
 * a single token: "YES" or "NO".
 *
 * <p>Interpretation: - "YES" (any case) -> passing=true, score=1.0 - any other output ->
 * passing=false, score=0.0
 *
 * <p>Input mapping: - EvaluationRequest.userText : question to be evaluated against -
 * EvaluationRequest.responseContent : candidate answer to judge
 *
 * <p>A ChatClient is built from the supplied builder for each evaluation call.
 *
 * @param chatClientBuilder builder used to create ChatClient instances
 */
@Component("ociGenAIRelevancyEvaluator")
public record OCIGenAIRelevancyEvaluator(
        ChatClient.Builder chatClientBuilder,
        @Value("classpath:/prompts/relevanceEvaluator/relevanceEvaluatorSystemPrompt.st")
                Resource systemPromptTemplate,
        @Value("classpath:/prompts/relevanceEvaluator/relevanceEvaluatorUserPrompt.st")
                Resource userPromptTemplate)
        implements Evaluator {

    /**
     * Executes the relevancy evaluation by constructing a system and user prompt and delegating the
     * judgment to the underlying chat model.
     *
     * @param evaluationRequest container holding the question (userText) and the candidate answer
     *     (responseContent)
     * @return EvaluationResponse with pass/fail and score (1.0 or 0.0)
     */
    @Override
    public org.springframework.ai.evaluation.EvaluationResponse evaluate(
            EvaluationRequest evaluationRequest) {

        var evaluationResult =
                this.chatClientBuilder
                        .build()
                        .prompt()
                        .system(systemPromptTemplate)
                        .user(
                                userSpec ->
                                        userSpec.text(userPromptTemplate)
                                                .param("question", evaluationRequest.getUserText())
                                                .param(
                                                        "answer",
                                                        evaluationRequest.getResponseContent()))
                        .call()
                        .entity(EvaluationResult.class);

        if (evaluationResult.relevant()) {
            return new org.springframework.ai.evaluation.EvaluationResponse(
                    true, 1.0F, "", Collections.emptyMap());
        }

        return new org.springframework.ai.evaluation.EvaluationResponse(
                false, 0.0F, "", Collections.emptyMap());
    }
}
