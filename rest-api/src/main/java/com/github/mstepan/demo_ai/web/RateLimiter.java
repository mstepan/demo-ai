package com.github.mstepan.demo_ai.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class RateLimiter {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final int permissionsCount;

    private final Duration timeWindow;

    private final AtomicInteger leftTokens;

    public static RateLimiter create(int permissionsCount, Duration timeWindow) {
        RateLimiter limiter = new RateLimiter(permissionsCount, timeWindow);
        Thread bucketRefillerThread =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    LOGGER.info(
                                            "Refiller thread started with sleep duration {}",
                                            limiter.timeWindow);

                                    while (!Thread.currentThread().isInterrupted()) {
                                        try {
                                            Thread.sleep(limiter.timeWindow);
                                            limiter.refill();
                                        } catch (InterruptedException interEx) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                });
        bucketRefillerThread.setName("Bucket-Refiller-Thread");

        return limiter;
    }

    public RateLimiter(int permissionsCount, Duration timeWindow) {
        this.permissionsCount = permissionsCount;
        this.timeWindow = timeWindow;
        this.leftTokens = new AtomicInteger(permissionsCount);
    }

    public boolean acquire() {
        return leftTokens.decrementAndGet() >= 0;
    }

    void refill() {
        while (true) {
            int currentTokensCount = leftTokens.get();
            if (leftTokens.compareAndSet(currentTokensCount, permissionsCount)) {
                break;
            }
        }
    }
}
