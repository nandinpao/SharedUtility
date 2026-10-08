package com.agitg.redisson.stream;

import java.time.Instant;
import java.util.Objects;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;

/** Explicit bounded polling endpoint for dashboards (caller selects stable stream/group list). */
public final class StreamPendingSnapshot {
    public record Snapshot(String stream, String group, long pending, long deadLetters, Instant sampledAt) { }
    private final RedissonClient redis;
    private final String deadLetterSuffix;
    public StreamPendingSnapshot(RedissonClient redis, String deadLetterSuffix) {
        this.redis = Objects.requireNonNull(redis);
        this.deadLetterSuffix = Objects.requireNonNull(deadLetterSuffix);
    }
    public Snapshot sample(String streamKey, String group) {
        if (streamKey == null || streamKey.isBlank() || group == null || group.isBlank()) {
            throw new IllegalArgumentException("stream and group required");
        }
        RStream<String,String> stream = redis.getStream(streamKey);
        var pending = stream.getPendingInfo(group);
        if (pending == null) throw new IllegalStateException("Missing PEL info; cannot report healthy");
        long dlq = redis.<String,String>getStream(streamKey + deadLetterSuffix).size();
        return new Snapshot(streamKey, group, pending.getTotal(), dlq, Instant.now());
    }
}
