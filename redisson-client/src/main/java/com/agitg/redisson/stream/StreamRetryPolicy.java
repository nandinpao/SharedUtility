package com.agitg.redisson.stream;

/** Immutable, bounded Redis Stream redelivery and dead-letter policy. */
public record StreamRetryPolicy(int maxDeliveries, String deadLetterSuffix) {

    public StreamRetryPolicy {
        if (maxDeliveries < 1) {
            throw new IllegalArgumentException("redis.stream.retry.max-deliveries must be >= 1");
        }
        if (deadLetterSuffix == null || deadLetterSuffix.isBlank() || deadLetterSuffix.contains(" ")) {
            throw new IllegalArgumentException("redis.stream.retry.dead-letter-suffix must be non-empty without spaces");
        }
    }

    public static StreamRetryPolicy defaults() {
        return new StreamRetryPolicy(5, ":dlq");
    }
}
