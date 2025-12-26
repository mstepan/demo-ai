package com.github.mstepan.demo_ai.evaluators;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Evaluator that verifies whether a natural-language claim is supported by a provided document,
 * using an OCI GenAI-backed ChatClient. The model is instructed to produce exactly one word: "yes"
 * or "no".
 *
 * <p>Interpretation: - "yes" (any case) -> passing=true - any other output -> passing=false
 *
 * <p>Input mapping: - EvaluationRequest.userText : the claim to verify - Document : a fixed test
 * document held in {@link #FACTS}
 *
 * <p>A ChatClient is built from the supplied builder for each evaluation call.
 *
 * @param chatClientBuilder builder used to create ChatClient instances
 */
@Component("ociGenAIFactsEvaluator")
public record OCIGenAIFactsEvaluator(ChatClient.Builder chatClientBuilder) implements Evaluator {

    /**
     * System prompt defining the role and strict single-word output format for the fact checking
     * task.
     */
    private static final PromptTemplate SYSTEM_PROMPT_TEMPLATE =
            new PromptTemplate(
"""
You are a fact verification assistant.
Your task is to determine whether a claim is supported by a given document.

Rules:
- Use ONLY the information explicitly stated in the document.
- Do NOT use prior knowledge, common sense, or inference beyond the document.
- If the claim is not clearly and directly supported, answer "no".
- If the document is ambiguous, incomplete, or silent about the claim, answer "no".
- Output MUST be exactly one word: "yes" or "no".
""");

    /**
     * User prompt template that injects the claim to verify and the document that serves as the
     * sole source of truth.
     */
    private static final PromptTemplate USER_PROMPT_PROMPT =
            new PromptTemplate(
"""
Evaluate whether the following claim is supported by the provided document.

A claim is considered "supported" ONLY IF the document explicitly states the claim or provides clear evidence that directly implies it.
If the document contradicts the claim, does not mention it, or provides insufficient information, respond with "no".

Use only the information in the document. Do not use external knowledge or assumptions.

Respond with exactly one word: "yes" or "no".

Claim: {claim}

Document: {document}
""");

    /**
     * Fixed "document" used during evaluation to keep tests deterministic. The claim is checked
     * strictly against this content only.
     */
    private static final String FACTS =
            """
            Here are few pirate names:
                1. Blackbeak Betty "The Buccaneer"
                2.*Captain Krael "The Kraken"
                3. Bartholomew "Blackheart" Blake
                4. Calico "The Corsair" Jack
                5. Mad Dog McSweeney "The Scourge"
            """;

    /**
     * Executes the fact verification by rendering the system and user prompts and delegating the
     * decision to the underlying chat model.
     *
     * <p>Mapping: - Model output "yes" (any case) -> passing=true - Otherwise -> passing=false
     *
     * @param evaluationRequest container holding the claim (userText)
     * @return EvaluationResponse indicating pass/fail; reason is empty
     */
    @Override
    public EvaluationResponse evaluate(EvaluationRequest evaluationRequest) {

        String evaluationResponse =
                this.chatClientBuilder
                        .build()
                        .prompt()
                        .system(SYSTEM_PROMPT_TEMPLATE.render())
                        .user(
                                USER_PROMPT_PROMPT.render(
                                        Map.of(
                                                "claim",
                                                evaluationRequest.getUserText(),
                                                "document",
                                                FACTS)))
                        .call()
                        .content();
        boolean passing = "yes".equalsIgnoreCase(evaluationResponse);
        return new EvaluationResponse(passing, "", Collections.emptyMap());
    }
}
