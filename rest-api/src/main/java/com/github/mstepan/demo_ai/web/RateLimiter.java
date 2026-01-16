package com.github.mstepan.demo_ai.web;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class RateLimiter {

    private final int PERMISSIONS_COUNT = 10;

    // 10 requests per second
    private final AtomicInteger leftTokens = new AtomicInteger(PERMISSIONS_COUNT);

    private final Duration timeWindow = Duration.ofSeconds(1);

    public static RateLimiter create() {
        RateLimiter limiter = new RateLimiter();
        Thread bucketRefillerThread =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try {
                                        Thread.sleep(limiter.timeWindow);
                                        limiter.refill();
                                    } catch (InterruptedException interEx) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
        bucketRefillerThread.setName("Bucket-Refiller-Thread");

        return limiter;
    }

    private RateLimiter() {}

    public boolean acquire() {
        return leftTokens.decrementAndGet() >= 0;
    }

    void refill() {
        while (true) {
            int currentTokensCount = leftTokens.get();
            if (leftTokens.compareAndSet(currentTokensCount, PERMISSIONS_COUNT)) {
                break;
            }
        }
    }
}
