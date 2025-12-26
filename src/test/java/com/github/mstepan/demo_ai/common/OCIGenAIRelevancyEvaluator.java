package com.github.mstepan.demo_ai.common;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

import java.util.Collections;
import java.util.Map;

/**
 * Evaluator that uses an OCI GenAI-backed ChatClient to decide whether a candidate
 * answer is relevant to and correctly addresses the provided question.
 * The model is instructed to reply with a single token: "YES" or "NO".
 *
 * Interpretation:
 * - "YES" (any case) -> passing=true, score=1.0
 * - any other output  -> passing=false, score=0.0
 *
 * Input mapping:
 * - EvaluationRequest.userText        : question to be evaluated against
 * - EvaluationRequest.responseContent : candidate answer to judge
 *
 * A ChatClient is built from the supplied builder for each evaluation call.
 *
 * @param chatClientBuilder builder used to create ChatClient instances
 */
public record OCIGenAIRelevancyEvaluator(ChatClient.Builder chatClientBuilder)
        implements Evaluator {

    /**
     * System prompt that defines the role and strict YES/NO output format
     * for the relevancy judgment task.
     */
    private static final PromptTemplate SYSTEM_PROMPT_TEMPLATE =
            new PromptTemplate(
"""
You are a relevance evaluation assistant.

Your task is to determine whether an answer is relevant to and correctly addresses the given question.

Rules:
- Respond with exactly one token: YES or NO.
- Do not include explanations, punctuation, or additional text.
- Ignore style, grammar, or verbosity; evaluate only relevance and correctness.
- If the answer does not address the question, is off-topic, or is factually incorrect, respond NO.
""");

    /**
     * User prompt template that injects the question and the candidate answer
     * to be judged for relevance.
     */
    private static final PromptTemplate USER_PROMPT_TEMPLATE =
            new PromptTemplate(
"""
Evaluate whether the following answer correctly and directly addresses the question.

Question:
{question}

Answer:
{answer}
""");

    /**
     * Executes the relevancy evaluation by constructing a system and user prompt
     * and delegating the judgment to the underlying chat model.
     *
     * @param evaluationRequest container holding the question (userText) and
     *                          the candidate answer (responseContent)
     * @return EvaluationResponse with pass/fail and score (1.0 or 0.0)
     */
    @Override
    public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.render();
        String userPrompt =
                USER_PROMPT_TEMPLATE.render(
                        Map.of(
                                "question",
                                evaluationRequest.getUserText(),
                                "answer",
                                evaluationRequest.getResponseContent()));

        String evaluationResponse =
                this.chatClientBuilder
                        .build()
                        .prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

        if ("yes".equalsIgnoreCase(evaluationResponse)) {
            return new EvaluationResponse(true, 1.0F, "", Collections.emptyMap());
        }

        return new EvaluationResponse(false, 0.0F, "", Collections.emptyMap());
    }
}
