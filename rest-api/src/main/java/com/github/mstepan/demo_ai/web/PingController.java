package com.github.mstepan.demo_ai.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/ping")
public class PingController {

    private final RateLimiter rateLimiter;

    public PingController() {
        //
        // http_200_reqs RPS: 34.09 req/s (count=1023, duration=30.01s)
        //
        rateLimiter = RateLimiter.create(33, Duration.ofSeconds(1));
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String ping() {
        if (rateLimiter.acquire()) {
            return "PONG";
        }
        throw new RateLimitExceededException();
    }
}
