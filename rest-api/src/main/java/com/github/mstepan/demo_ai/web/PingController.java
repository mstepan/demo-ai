package com.github.mstepan.demo_ai.web;

import com.github.mstepan.demo_ai.rate.RateLimitExceededException;
import com.github.mstepan.demo_ai.rate.RateLimiter;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/ping")
public class PingController {

    private final RateLimiter rateLimiter;

    public PingController(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String ping() {
        if (rateLimiter.acquire()) {
            return "PONG";
        }
        throw new RateLimitExceededException();
    }
}
