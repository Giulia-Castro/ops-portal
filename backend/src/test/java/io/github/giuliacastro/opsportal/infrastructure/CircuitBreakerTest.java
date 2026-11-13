package io.github.giuliacastro.opsportal.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.giuliacastro.opsportal.infrastructure.http.CircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void opensAfterThresholdAndRecoversAfterTheOpenPeriod() {
        var clock = new MutableClock();
        var breaker = new CircuitBreaker(2, Duration.ofSeconds(30), clock);

        breaker.recordFailure();
        assertThat(breaker.allowsCall()).isTrue();
        breaker.recordFailure();
        assertThat(breaker.isOpen()).isTrue();

        clock.now = clock.now.plusSeconds(31);
        assertThat(breaker.allowsCall()).isTrue();
        breaker.recordSuccess();
        breaker.recordFailure();
        assertThat(breaker.allowsCall()).isTrue();
    }
}
