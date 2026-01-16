package com.github.mstepan.demo_ai.rate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Qualifier("tokenBucketRateLimiter")
public final class TokenBucketRateLimiter implements RateLimiter {

    private final int permissionsCount;
    private final Duration timeWindow;

    private final AtomicReference<LimiterState> state;

    record LimiterState(long lastRefillTime, int availablePermits) {}

    //
    // http_200_reqs RPS: 33.00 req/s (count=1980, duration=60.00s)
    //
    public TokenBucketRateLimiter(
            @Value("${rate-limiter.permissions:33}") int permissionsCount,
            @Value("${rate-limiter.window:1s}") Duration timeWindow) {
        if (permissionsCount <= 0) {
            throw new IllegalArgumentException("'permissionsCount' must be greater than 0");
        }
        if (timeWindow == null || timeWindow.toMillis() <= 0L) {
            throw new IllegalArgumentException("'timeWindow' is null, or zero, or negative");
        }

        this.permissionsCount = permissionsCount;
        this.timeWindow = timeWindow;
        this.state = new AtomicReference<>(new LimiterState(System.nanoTime(), permissionsCount));
    }

    @Override
    public boolean acquire() {
        checkIfRefillNeeded();

        while (true) {
            LimiterState curState = state.get();
            if (curState.availablePermits <= 0) {
                return false;
            }
            if (state.compareAndSet(
                    curState,
                    new LimiterState(curState.lastRefillTime, curState.availablePermits - 1))) {
                return true;
            }

            Thread.onSpinWait();
        }
    }

    void checkIfRefillNeeded() {
        final long now = System.nanoTime();

        while (true) {
            LimiterState curState = state.get();

            if ((now - curState.lastRefillTime) < timeWindow.toNanos()) {
                break;
            }
            if (state.compareAndSet(curState, new LimiterState(now, permissionsCount))) {
                break;
            }

            Thread.onSpinWait();
        }
    }
}
