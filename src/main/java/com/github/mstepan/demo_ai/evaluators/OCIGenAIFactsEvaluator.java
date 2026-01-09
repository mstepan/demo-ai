package com.github.mstepan.demo_ai.evaluators;

import com.github.mstepan.demo_ai.domain.EvaluationResult;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;

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
public record OCIGenAIFactsEvaluator(
        ChatClient.Builder chatClientBuilder,
        @Value("classpath:/prompts/factsEvaluator/factsEvaluatorSystemPrompt.st")
                Resource systemPromptTemplate,
        @Value("classpath:/prompts/factsEvaluator/factsEvaluatorUserPrompt.st")
                Resource userPromptTemplate)
        implements Evaluator {

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

        var evaluationResult =
                this.chatClientBuilder
                        .build()
                        .prompt()
                        .system(systemPromptTemplate)
                        .user(
                                userSpec ->
                                        userSpec.text(userPromptTemplate)
                                                .param("claim", evaluationRequest.getUserText())
                                                .param("document", FACTS))
                        .call()
                        .entity(EvaluationResult.class);

        return new EvaluationResponse(evaluationResult.relevant(), "", Collections.emptyMap());
    }
}
