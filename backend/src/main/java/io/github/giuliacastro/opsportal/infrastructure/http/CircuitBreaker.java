package io.github.giuliacastro.opsportal.infrastructure.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Minimal circuit breaker: after N consecutive failures, calls fail fast for a while instead of
 * piling up on a dead upstream. One half-open probe is allowed after the open period.
 */
public final class CircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private int consecutiveFailures;
    private Instant openUntil = Instant.MIN;

    public CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public synchronized boolean allowsCall() {
        return !clock.instant().isBefore(openUntil);
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntil = Instant.MIN;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openUntil = clock.instant().plus(openDuration);
        }
    }

    public synchronized boolean isOpen() {
        return !allowsCall();
    }
}
