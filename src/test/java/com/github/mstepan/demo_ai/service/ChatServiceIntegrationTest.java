package com.github.mstepan.demo_ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.mstepan.demo_ai.common.OCIGenAIFactsEvaluator;
import com.github.mstepan.demo_ai.common.OCIGenAIRelevancyEvaluator;
import com.github.mstepan.demo_ai.web.Question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("exhaustive")
public class ChatServiceIntegrationTest {

    @Autowired ChatService chatService;

    @Autowired ChatClient.Builder chatClientBuilder;

    Evaluator relevancyEvaluator;

    Evaluator factsEvaluator;

    @BeforeEach
    void setUp() {
        relevancyEvaluator = new OCIGenAIRelevancyEvaluator(chatClientBuilder);
        factsEvaluator = new OCIGenAIFactsEvaluator(chatClientBuilder);
    }

    @Test
    void evaluateRelevancy() {
        var userQuestion = "Generate top 5 pirate names.";
        var answer = chatService.askQuestion(new Question(userQuestion));

        var evalRequest = new EvaluationRequest(userQuestion, answer.answer());
        var response = relevancyEvaluator.evaluate(evalRequest);

        assertThat(response.isPass())
                .withFailMessage(
                        """
        ===============================================================
        THE ANSWER %n %s %n IS NOT RELEVANT TO QUESTION %n %s %n.
        ===============================================================
        """,
                        answer.answer(), userQuestion)
                .isTrue();
    }

    @Test
    void evaluateFactsAccuracy() {
        var userQuestion = "Generate top 5 pirate names.";
        var answer = chatService.askQuestion(new Question(userQuestion));

        var evalRequest = new EvaluationRequest(userQuestion, answer.answer());
        var response = factsEvaluator.evaluate(evalRequest);

        assertThat(response.isPass())
                .withFailMessage(
                        """
        ===============================================================
        THE ANSWER %n %s %n IS NOT ACCORDING TO FACTS FOR QUESTION %n %s %n.
        ===============================================================
        """,
                        answer.answer(), userQuestion)
                .isTrue();
    }
}
