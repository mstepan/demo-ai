package com.github.mstepan.demo_ai.rate;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RateLimiter implements AutoCloseable {

    private final int permissionsCount;

    private final AtomicInteger leftTokens;

    private ScheduledExecutorService scheduledExecutor;

    public static RateLimiter create(int permissionsCount, Duration timeWindow) {
        RateLimiter limiter = new RateLimiter(permissionsCount, timeWindow);

        ScheduledExecutorService scheduledExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "Bucket-Refiller-Thread");
                            thread.setDaemon(true);
                            return thread;
                        });
        scheduledExecutor.scheduleAtFixedRate(
                limiter::refill, 0L, timeWindow.toMillis(), TimeUnit.MILLISECONDS);

        limiter.scheduledExecutor = scheduledExecutor;

        return limiter;
    }

    @Override
    public void close() {
        this.scheduledExecutor.shutdown();
    }

    private RateLimiter(int permissionsCount, Duration timeWindow) {
        if (permissionsCount <= 0) {
            throw new IllegalArgumentException("permissionsCount must be greater than 0");
        }
        if (timeWindow == null) {
            throw new IllegalArgumentException("timeWindow cannot be null");
        }
        this.permissionsCount = permissionsCount;
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
