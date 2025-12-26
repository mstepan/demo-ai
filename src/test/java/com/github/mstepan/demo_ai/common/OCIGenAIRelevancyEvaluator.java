package com.github.mstepan.demo_ai.common;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

import java.util.Collections;
import java.util.Map;

public record OCIGenAIRelevancyEvaluator(ChatClient.Builder chatClientBuilder)
        implements Evaluator {

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

    private static final PromptTemplate USER_PROMPT_TEMPLATE =
            new PromptTemplate(
"""
Evaluate whether the following answer correctly and directly addresses the question.

Question:
{question}

Answer:
{answer}
""");

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
