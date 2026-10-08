package com.agitg.redisson.stream;

import java.util.concurrent.ThreadLocalRandom;

/** Exponential error backoff with bounded equal-jitter. No unbounded tight retry loops. */
public final class StreamReconnectBackoff {
    private final long initialMillis;
    private final long maxMillis;
    private int consecutiveFailures;

    public StreamReconnectBackoff(long initialMillis, long maxMillis) {
        if (initialMillis < 1 || maxMillis < initialMillis) {
            throw new IllegalArgumentException("invalid reconnect backoff bounds");
        }
        this.initialMillis = initialMillis;
        this.maxMillis = maxMillis;
    }
    public static StreamReconnectBackoff defaults() {
        return new StreamReconnectBackoff(200, 5000);
    }
    public void succeeded() { consecutiveFailures = 0; }
    public int failures() { return consecutiveFailures; }

    /** Pure calculation for repeatable testing; jitterUnit must be within [0,1]. */
    public long delayMillis(double jitterUnit) {
        if (jitterUnit < 0 || jitterUnit > 1 || !Double.isFinite(jitterUnit)) {
            throw new IllegalArgumentException("jitterUnit out of range");
        }
        consecutiveFailures = Math.min(consecutiveFailures + 1, 31);
        long cap = initialMillis;
        for (int i = 1; i < consecutiveFailures; i++) {
            cap = cap > maxMillis / 2 ? maxMillis : Math.min(maxMillis, cap * 2);
        }
        return Math.max(1L, (long) (cap / 2.0 + (cap / 2.0) * jitterUnit));
    }
    public boolean pause() {
        long millis = delayMillis(ThreadLocalRandom.current().nextDouble());
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
