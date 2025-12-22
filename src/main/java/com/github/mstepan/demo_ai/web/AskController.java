package com.github.mstepan.demo_ai.web;

import com.github.mstepan.demo_ai.service.ChatService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

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
        LOGGER.info("ask LLM question {}", question.question());

        return chatService.askQuestion(question);
    }
}
