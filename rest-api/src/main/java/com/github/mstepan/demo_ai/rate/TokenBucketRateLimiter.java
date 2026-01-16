package com.github.mstepan.demo_ai.rate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class TokenBucketRateLimiter implements AutoCloseable {

    private final int permissionsCount;
    private final Duration timeWindow;

    private final AtomicInteger availablePermits = new AtomicInteger();

    private volatile ScheduledExecutorService scheduledExecutor;

    public TokenBucketRateLimiter(
            @Value("${rate-limiter.permissions:10}") int permissionsCount,
            @Value("${rate-limiter.window:1s}") Duration timeWindow) {
        if (permissionsCount <= 0) {
            throw new IllegalArgumentException("'permissionsCount' must be greater than 0");
        }
        if (timeWindow == null || timeWindow.toMillis() <= 0L) {
            throw new IllegalArgumentException("'timeWindow' is null, or zero, or negative");
        }

        this.permissionsCount = permissionsCount;
        this.timeWindow = timeWindow;
        this.availablePermits.set(permissionsCount);
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
        executor.scheduleAtFixedRate(
                this::refill, timeWindow.toMillis(), timeWindow.toMillis(), TimeUnit.MILLISECONDS);
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
            try {
                this.scheduledExecutor.awaitTermination(1L, TimeUnit.SECONDS);
            } catch (InterruptedException interEx) {
                Thread.currentThread().interrupt();
                this.scheduledExecutor.shutdownNow();
            }
        }
    }

    public boolean acquire() {
        while (true) {
            final int currentTokens = availablePermits.get();
            if (currentTokens <= 0) {
                return false;
            }
            if (availablePermits.compareAndSet(currentTokens, currentTokens - 1)) {
                return true;
            }
        }
    }

    void refill() {
        availablePermits.set(permissionsCount);
    }
}
