package com.agitg.redisson.stream;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;

/** Explicit, audited-call-site API only: never schedules automatic replay or deletes DLQ.
 * Caller MUST authorize operator input and use JdbcStreamIdempotencyGuard for business writes.
 */
public final class StreamReplayPublisher {
    private final RedissonClient redis;
    public StreamReplayPublisher(RedissonClient redis) { this.redis = Objects.requireNonNull(redis); }
    public StreamMessageId publishApproved(StreamReplayPlanner.Plan plan) {
        Objects.requireNonNull(plan);
        Map<String,String> body = new LinkedHashMap<>(plan.payload());
        body.put("__originStream", plan.sourceStream());
        body.put("__originGroup", plan.sourceGroup());
        body.put("__originId", plan.sourceMessageId());
        StreamMessageId newId = redis.<String,String>getStream(plan.sourceStream())
                .add(StreamAddArgs.entries(body));
        if (newId == null) throw new IllegalStateException("Replay XADD returned no ID");
        // DLQ entry intentionally remains for audit; duplicate publishes are possible.
        return newId;
    }

    /** Use this only for authenticated, trusted replay producers; never accept origin keys
     * from untrusted external publishers because they can spoof an idempotency identity.
     */
    public static String trustedOriginId(Map<String,String> body, String fallbackId) {
        if (body == null || !body.containsKey("__originId")) return fallbackId;
        String id = body.get("__originId");
        if (id == null || !id.matches("[0-9]+-[0-9]+")) {
            throw new IllegalArgumentException("Invalid replay origin message id");
        }
        return id;
    }
}
