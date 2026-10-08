package com.agitg.redisson.stream;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.redisson.api.stream.PendingEntry;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;

/**
 * One authoritative processing/ACK/DLQ implementation for both consumer modes.
 *
 * Delivery guarantee: at-least-once, NEVER exactly-once. The business callback MUST be
 * idempotent using (stream, group, messageId) or a domain idempotency key in a durable
 * store, ideally in the same transaction as the business side-effect.
 *
 * DLQ append and source ACK are not atomic across Redis keys. If ACK fails after a
 * successful XADD, the DLQ may contain duplicates. Replayers MUST deduplicate using
 * (sourceStream, sourceGroup, sourceMessageId). No source ACK on DLQ write failure.
 */
public final class StreamDeliveryProcessor {

    public enum Outcome {
        ACKED, ACK_DEFERRED, ACK_FAILED, LEFT_PENDING, DEAD_LETTERED, DLQ_FAILED
    }

    private final RedissonClient redisson;
    private final StreamRetryPolicy retryPolicy;
    private final StreamDeadLetterPolicy deadLetterPolicy;
    private static final System.Logger LOGGER = System.getLogger(StreamDeliveryProcessor.class.getName());

    public StreamDeliveryProcessor(RedissonClient redisson, StreamRetryPolicy retryPolicy) {
        this(redisson, retryPolicy, StreamDeadLetterPolicy.metadataOnly());
    }

    public StreamDeliveryProcessor(RedissonClient redisson, StreamRetryPolicy retryPolicy,
                                   StreamDeadLetterPolicy deadLetterPolicy) {
        this.redisson = Objects.requireNonNull(redisson, "redisson");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.deadLetterPolicy = Objects.requireNonNull(deadLetterPolicy, "deadLetterPolicy");
    }

    public Outcome process(RStream<String, String> stream, StreamConsumerConfig config,
                           StreamMessageId id, Map<String, String> body, Runnable callback) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(callback, "callback");
        try {
            callback.run();
            if (!config.isAutoAck()) {
                return Outcome.ACK_DEFERRED;
            }
            return ackWithRetry(stream, config.getGroup(), id) ? Outcome.ACKED : Outcome.ACK_FAILED;
        } catch (Exception error) {
            // Do not log message body or exception text: these can contain customer data.
            LOGGER.log(System.Logger.Level.WARNING,
                    "Redis stream callback failed stream={0} id={1} exceptionType={2}",
                    config.getStreamKey(), id, error.getClass().getName());
            final int deliveries;
            try {
                deliveries = deliveryCount(stream, config.getGroup(), id);
            } catch (RuntimeException pendingReadError) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Unable to inspect Redis PEL stream={0} id={1}; leaving pending",
                        config.getStreamKey(), id);
                return Outcome.LEFT_PENDING;
            }
            if (deliveries < retryPolicy.maxDeliveries()) {
                return Outcome.LEFT_PENDING;
            }
            try {
                appendDeadLetter(config, id, body, deliveries, error);
            } catch (RuntimeException dlqError) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Redis dead-letter append failed stream={0} id={1}; leaving pending",
                        config.getStreamKey(), id);
                return Outcome.DLQ_FAILED;
            }
            return ackWithRetry(stream, config.getGroup(), id) ? Outcome.DEAD_LETTERED : Outcome.ACK_FAILED;
        }
    }

    /** Returns 0 if the PEL entry is missing/ambiguous. Fail closed. */
    int deliveryCount(RStream<String, String> stream, String group, StreamMessageId id) {
        List<PendingEntry> pending = stream.listPending(group, id, id, 1);
        if (pending == null || pending.isEmpty() || !id.equals(pending.get(0).getId())) {
            return 0;
        }
        long count = pending.get(0).getDeliveryCount();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, count));
    }

    private void appendDeadLetter(StreamConsumerConfig config, StreamMessageId id,
                                  Map<String, String> body, int deliveries, Exception error) {
        Map<String, String> dlq = new LinkedHashMap<>();
        dlq.put("sourceStream", config.getStreamKey());
        dlq.put("sourceGroup", config.getGroup());
        dlq.put("sourceMessageId", id.toString());
        dlq.put("sourceConsumer", config.getConsumer());
        dlq.put("deliveries", Integer.toString(deliveries));
        dlq.put("failedAt", Instant.now().toString());
        dlq.put("errorClass", error.getClass().getName());
        dlq.putAll(deadLetterPolicy.sanitizedBody(body));
        RStream<String, String> deadLetters = redisson.getStream(config.getStreamKey() + retryPolicy.deadLetterSuffix());
        StreamMessageId newId = deadLetters.add(StreamAddArgs.entries(dlq));
        if (newId == null) {
            throw new IllegalStateException("Dead-letter XADD returned no ID");
        }
    }

    /** ACK=1 is success. ACK=0, exceptions or interruption are NOT success. */
    public boolean ackWithRetry(RStream<String, String> stream, String group, StreamMessageId id) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                long acknowledged = stream.ack(group, id);
                return acknowledged == 1L;
            } catch (RuntimeException error) {
                if (attempt == 3) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Redis ACK failed group={0} id={1} attempts={2}", group, id, attempt);
                    return false;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /** Manual ACK never deletes an entry if XACK returned 0. */
    public boolean ackManually(String streamKey, String group, StreamMessageId id, boolean deleteAfterAck) {
        RStream<String, String> stream = redisson.getStream(streamKey);
        if (!ackWithRetry(stream, group, id)) {
            return false;
        }
        if (deleteAfterAck) {
            try {
                stream.remove(id);
            } catch (RuntimeException removeError) {
                // XACK already succeeded; report truthfully instead of re-ACKing on retry.
                LOGGER.log(System.Logger.Level.WARNING,
                        "Redis XDEL failed after successful XACK stream={0} id={1}", streamKey, id);
            }
        }
        return true;
    }
}
