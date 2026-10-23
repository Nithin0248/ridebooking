package com.nithin.ridebooking.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptTrackerTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private AttemptTracker tracker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        tracker = new AttemptTracker(3, Duration.ofMinutes(10), clock);
    }

    @Test
    void blocksOnlyAfterTheLimitIsReached() {
        tracker.record("a");
        tracker.record("a");
        assertThat(tracker.isBlocked("a")).isFalse();

        tracker.record("a");
        assertThat(tracker.isBlocked("a")).isTrue();
    }

    @Test
    void unblocksWhenTheWindowEnds() {
        for (int i = 0; i < 3; i++) tracker.record("a");
        assertThat(tracker.isBlocked("a")).isTrue();

        clock.advance(Duration.ofMinutes(10));

        assertThat(tracker.isBlocked("a")).isFalse();
    }

    @Test
    void resetClearsTheCount() {
        for (int i = 0; i < 3; i++) tracker.record("a");
        tracker.reset("a");

        assertThat(tracker.isBlocked("a")).isFalse();
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) tracker.record("a");

        assertThat(tracker.isBlocked("a")).isTrue();
        assertThat(tracker.isBlocked("b")).isFalse();
    }

    @Test
    void retryAfterShowsTheTimeLeftInTheWindow() {
        for (int i = 0; i < 3; i++) tracker.record("a");
        clock.advance(Duration.ofMinutes(4));

        assertThat(tracker.retryAfterSeconds("a")).isEqualTo(360);
        assertThat(tracker.retryAfterSeconds("unknown")).isZero();
    }
}