package com.agitg.redisson.stream;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.redisson.api.stream.AutoClaimResult;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamMessageId;

/** Cursor-driven XAUTOCLAIM: empty pages may still have a next cursor. */
public final class StreamPendingClaimer {

    /** A Cursor belongs to exactly one consumer loop and persists across polling passes. */
    public static final class Cursor {
        private StreamMessageId next = StreamMessageId.MIN;
        public StreamMessageId next() { return next; }
    }

    /** Returns number of delivered entries, not number of successful business callbacks. */
    public int scan(RStream<String, String> stream, StreamConsumerConfig config,
                    AutoClaimPolicy policy, Cursor cursor,
                    BiConsumer<StreamMessageId, Map<String, String>> handler) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(handler, "handler");
        if (!policy.enabled()) return 0;

        int delivered = 0;
        for (int round = 0; round < policy.maxRounds(); round++) {
            StreamMessageId start = cursor.next;
            AutoClaimResult<String, String> result = stream.autoClaim(
                    config.getGroup(), config.getConsumer(), policy.idle().toMillis(),
                    TimeUnit.MILLISECONDS, start, policy.batch());
            if (result == null) return delivered;

            // Even when no messages meet min-idle, Redis may advance the cursor.
            StreamMessageId next = result.getNextId();
            if (next == null || next.equals(start) || StreamMessageId.MIN.equals(next)) {
                cursor.next = StreamMessageId.MIN;
            } else {
                cursor.next = next;
            }
            Map<StreamMessageId, Map<String, String>> messages = result.getMessages();
            if (messages != null) {
                for (var entry : messages.entrySet()) {
                    handler.accept(entry.getKey(), entry.getValue());
                    delivered++;
                }
            }
            // Once the server signals a full scan, restart from 0 on a future tick.
            if (StreamMessageId.MIN.equals(cursor.next)) break;
        }
        return delivered;
    }
}
