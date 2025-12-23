package com.github.mstepan.demo_ai.service;

import com.github.mstepan.demo_ai.web.Answer;
import com.github.mstepan.demo_ai.web.Question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.lang.invoke.MethodHandles;

@Service
public class ChatService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public Answer askQuestion(Question question) {
        var answerText = chatClient.prompt().user(question.question()).call().content();
        return new Answer(answerText);
    }

    public Flux<String> streamAnswer(Question question) {
        // Stream directly using the primary ChatClient (backed by OCIChatModel which implements StreamingChatModel)
        return chatClient
                .prompt()
                .user(question.question())
                .stream()
                .content()
                // Run the blocking OCI stream reading on a bounded elastic thread,
                // so the servlet request thread is not blocked and SSE can flush per chunk.
                .subscribeOn(Schedulers.boundedElastic());
    }
}
