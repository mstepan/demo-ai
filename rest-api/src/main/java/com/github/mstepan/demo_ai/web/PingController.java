package com.github.mstepan.demo_ai.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ping")
public class PingController {

    private static final RateLimiter rateLimiter = RateLimiter.create();

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String ping() {
        if (rateLimiter.acquire()) {
            return "PONG";
        }
        throw new RateLimitExceededException();
    }
}
