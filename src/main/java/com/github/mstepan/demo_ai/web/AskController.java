package com.github.mstepan.demo_ai.web;

import com.github.mstepan.demo_ai.service.ChatService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SseEmitter> askChatStream(@Valid @RequestBody Question question) {
        // 0L disables default 30s timeout so long generations can complete
        SseEmitter emitter = new SseEmitter(0L);

        // Disable proxy buffering and caching to ensure immediate delivery of events
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        headers.set("X-Accel-Buffering", "no");

        // Send an initial event to open the stream on some clients/proxies
        try {
            emitter.send(SseEmitter.event().name("ready").data("", MediaType.TEXT_PLAIN));
        } catch (Exception initEx) {
            LOGGER.warn("Failed to send initial SSE event", initEx);
        }

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
                                        LOGGER.info("Emitting SSE event");
                                        emitter.send(SseEmitter.event().name("delta").data(chunk, MediaType.TEXT_PLAIN));
                                    } catch (Exception sendEx) {
                                        LOGGER.warn("SSE send failed", sendEx);
                                        emitter.completeWithError(sendEx);
                                    }
                                },
                                error -> emitter.completeWithError(error),
                                emitter::complete);

        return ResponseEntity.ok().headers(headers).body(emitter);
    }
}
