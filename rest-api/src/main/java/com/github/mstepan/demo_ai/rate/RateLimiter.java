package com.github.mstepan.demo_ai.rate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class RateLimiter implements AutoCloseable {

    private final int permissionsCount;
    private final Duration timeWindow;

    private final AtomicInteger leftTokens = new AtomicInteger();

    private ScheduledExecutorService scheduledExecutor;

    public RateLimiter(
            @Value("${rate-limiter.permissions:10}") int permissionsCount,
            @Value("${rate-limiter.window:1s}") Duration timeWindow) {
        if (permissionsCount <= 0) {
            throw new IllegalArgumentException("permissionsCount must be greater than 0");
        }
        this.permissionsCount = permissionsCount;
        this.timeWindow = Objects.requireNonNull(timeWindow, "timeWindow cannot be null");
        this.leftTokens.set(permissionsCount);
    }

    @PostConstruct
    void init() {
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "Bucket-Refiller-Thread");
                            thread.setDaemon(true);
                            return thread;
                        });
        executor.scheduleAtFixedRate(this::refill, 0L, timeWindow.toMillis(), TimeUnit.MILLISECONDS);
        this.scheduledExecutor = executor;
    }

    @PreDestroy
    void onShutdown() {
        close();
    }

    @Override
    public void close() {
        if (this.scheduledExecutor != null) {
            this.scheduledExecutor.shutdown();
        }
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
