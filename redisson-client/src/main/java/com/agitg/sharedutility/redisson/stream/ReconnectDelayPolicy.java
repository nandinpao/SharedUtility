package com.agitg.sharedutility.redisson.stream;

/** Pure backoff calculation; deliberately retains the pre-existing equal-jitter rounding. */
public final class ReconnectDelayPolicy {
    private ReconnectDelayPolicy() { }

    public static long delayMillis(long initialMillis, long maxMillis,
                                   int failureCount, double jitterUnit) {
        if (jitterUnit < 0 || jitterUnit > 1 || !Double.isFinite(jitterUnit)) {
            throw new IllegalArgumentException("jitterUnit out of range");
        }
        if (initialMillis < 1 || maxMillis < initialMillis || failureCount < 1) {
            throw new IllegalArgumentException("invalid reconnect backoff bounds");
        }
        long cap = initialMillis;
        for (int i = 1; i < Math.min(failureCount, 31); i++) {
            cap = cap > maxMillis / 2 ? maxMillis : Math.min(maxMillis, cap * 2);
        }
        return Math.max(1L, (long) (cap / 2.0 + (cap / 2.0) * jitterUnit));
    }
}
