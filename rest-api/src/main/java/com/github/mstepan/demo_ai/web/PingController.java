package com.github.mstepan.demo_ai.web;

import com.github.mstepan.demo_ai.rate.RateLimitExceededException;
import com.github.mstepan.demo_ai.rate.RateLimiter;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/ping")
public class PingController {

    private final RateLimiter rateLimiter;

    public PingController() {
        //
        // http_200_reqs RPS: 10.17 req/s (count=610, duration=60.00s)
        //
        rateLimiter = RateLimiter.create(10, Duration.ofSeconds(1));
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String ping() {
        if (rateLimiter.acquire()) {
            return "PONG";
        }
        throw new RateLimitExceededException();
    }
}
