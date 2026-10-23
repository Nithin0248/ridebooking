package com.nithin.ridebooking.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Counts events per key inside a fixed time window. Thread-safe, in memory. */
public class AttemptTracker {

    private record Entry(Instant windowStart, int count) {}

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public AttemptTracker(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Entry entry = entries.get(key);
        return entry != null
                && !isExpired(entry, clock.instant())
                && entry.count() >= maxAttempts;
    }

    public void record(String key) {
        Instant now = clock.instant();
        entries.compute(key, (k, old) ->
                (old == null || isExpired(old, now))
                        ? new Entry(now, 1)
                        : new Entry(old.windowStart(), old.count() + 1));

        // stops the map growing forever if someone sends requests from many addresses
        if (entries.size() > CLEANUP_THRESHOLD) {
            entries.values().removeIf(e -> isExpired(e, now));
        }
    }

    public void reset(String key) {
        entries.remove(key);
    }

    public long retryAfterSeconds(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return 0;
        }
        long left = Duration.between(clock.instant(), entry.windowStart().plus(window)).getSeconds();
        return Math.max(left, 1);
    }

    private boolean isExpired(Entry entry, Instant now) {
        return !now.isBefore(entry.windowStart().plus(window));
    }
}