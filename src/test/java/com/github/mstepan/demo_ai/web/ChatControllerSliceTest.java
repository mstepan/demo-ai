package com.github.mstepan.demo_ai.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mstepan.demo_ai.service.ChatService;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import reactor.core.publisher.Flux;

@WebMvcTest(controllers = ChatController.class)
@Import(ExceptionHandlerAdvice.class)
class ChatControllerSliceTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private ChatService chatService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public ChatService mockChatService() {
            return Mockito.mock(ChatService.class);
        }
    }

    @Test
    void askReturnsAnswerAsJSON() throws Exception {
        // given
        final Question requestQuestion = new Question("Why sky is blue?");
        final String answerText = "The main reason why sky is blue is because it's blue.";
        final Answer reponseAnswer = new Answer(answerText);

        when(chatService.askQuestion(eq(requestQuestion))).thenReturn(reponseAnswer);

        // when/then
        mockMvc.perform(
                        post("/ask")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestQuestion)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value(answerText));

        verify(chatService, times(1)).askQuestion(eq(requestQuestion));
        verifyNoMoreInteractions(chatService);
    }

    @Test
    void askReturnsBadRequestOnValidationFailure() throws Exception {
        // given: invalid input (blank question)
        String invalidJson =
                """
        {"question":""}
        """;

        // when/then
        mockMvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON).content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$['invalid-params'][0]").value("'question' is required field"));

        verifyNoInteractions(chatService);
    }

    @Test
    void askStreamReturnsNDJSONStream() throws Exception {
        // given
        Question request = new Question("stream please");
        when(chatService.askQuestionStreaming(eq(request))).thenReturn(Flux.just("part1", "part2"));

        // when/then
        mockMvc.perform(
                        post("/ask/stream")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_NDJSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON))
                .andExpect(content().string(containsString("part1")))
                .andExpect(content().string(containsString("part2")));

        verify(chatService, times(1)).askQuestionStreaming(eq(request));
        verifyNoMoreInteractions(chatService);
    }
}
