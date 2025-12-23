package com.github.mstepan.demo_ai.web;

import com.github.mstepan.demo_ai.service.ChatService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.Disposable;

import java.lang.invoke.MethodHandles;

import javax.validation.Valid;

@RestController
public class AskController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ChatService chatService;

    public AskController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(path = "/ask", produces = "application/json")
    public Answer askChat(@Valid @RequestBody Question question) {
        return chatService.askQuestion(question);
    }

    @PostMapping(path = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askChatStream(@Valid @RequestBody Question question) {
        // 0L disables default 30s timeout so long generations can complete
        SseEmitter emitter = new SseEmitter(0L);

        final Disposable[] subscription = new Disposable[1];

        emitter.onCompletion(
                () -> {
                    if (subscription[0] != null && !subscription[0].isDisposed()) {
                        subscription[0].dispose();
                    }
                });
        emitter.onTimeout(
                () -> {
                    if (subscription[0] != null && !subscription[0].isDisposed()) {
                        subscription[0].dispose();
                    }
                    emitter.complete();
                });

        subscription[0] =
                chatService
                        .streamAnswer(question)
                        .subscribe(
                                chunk -> {
                                    try {
                                        emitter.send(SseEmitter.event().data(chunk));
                                    } catch (Exception sendEx) {
                                        LOGGER.warn("SSE send failed", sendEx);
                                        emitter.completeWithError(sendEx);
                                    }
                                },
                                error -> emitter.completeWithError(error),
                                emitter::complete);

        return emitter;
    }
}
