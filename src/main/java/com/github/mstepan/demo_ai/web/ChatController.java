package com.github.mstepan.demo_ai.web;

import com.github.mstepan.demo_ai.service.ChatService;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;

import javax.validation.Valid;

@RestController
@RequestMapping("/ask")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Answer ask(@Valid @RequestBody Question question) {
        return chatService.askQuestion(question);
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askStream(@Valid @RequestBody Question question) {
        return chatService
                .askQuestionStreaming(question)
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }
}
