package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Does not own the shared RedissonClient or any caller-provided ExecutorService. */
public class StreamTaskConsumer implements DisposableBean {

    private record TaskKey(String stream, String group, String consumer) {}
    private final RedissonClient redisson;
    private final AutoClaimPolicyResolver policyResolver;
    private final StreamDeliveryProcessor processor;
    private final StreamPendingClaimer pendingClaimer = new StreamPendingClaimer();
    private final ConcurrentHashMap<TaskKey, FutureTask<Void>> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private static final System.Logger LOGGER = System.getLogger(StreamTaskConsumer.class.getName());

    @Autowired(required = false)
    @Nullable
    private MeterRegistry meterRegistry;

    /** Legacy constructor for manually created consumers (default retry/DLQ policy). */
    public StreamTaskConsumer(RedissonClient redisson, AutoClaimPolicyResolver resolver) {
        this(redisson, resolver, new StreamDeliveryProcessor(redisson, StreamRetryPolicy.defaults()));
    }

    @Autowired
    public StreamTaskConsumer(RedissonClient redisson, AutoClaimPolicyResolver resolver,
                              StreamDeliveryProcessor processor) {
        this.redisson = Objects.requireNonNull(redisson);
        this.policyResolver = Objects.requireNonNull(resolver);
        this.processor = Objects.requireNonNull(processor);
    }

    public <T> void startConsuming(StreamConsumerConfig config, Duration block,
                                   Function<Map<String, String>, T> mapper,
                                   ExecutorService executor, StreamTaskListener<T> listener) {
        startConsuming(config, block, mapper, executor, listener, null);
    }

    public <T> void startConsuming(StreamConsumerConfig config, Duration block,
                                   Function<Map<String, String>, T> mapper,
                                   ExecutorService executor, StreamTaskListener<T> listener,
                                   @Nullable AutoClaimPolicy overridePolicy) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(executor, "executor");
        validate(config, block);
        // Snapshot caller-owned mutable bean; a later setter must not redirect a running task.
        final StreamConsumerConfig snapshot = new StreamConsumerConfig(config.getStreamKey(), config.getGroup(),
                config.getConsumer(), config.isAutoAck(), config.isAutoCreateGroup());
        if (closed.get()) throw new IllegalStateException("StreamTaskConsumer is closed");

        TaskKey key = new TaskKey(snapshot.getStreamKey(), snapshot.getGroup(), snapshot.getConsumer());
        AutoClaimPolicy policy = policyResolver.resolve(snapshot, overridePolicy);
        RStream<String, String> stream = redisson.getStream(snapshot.getStreamKey());
        var args = StreamReadGroupArgs.neverDelivered().count(10).timeout(block);
        var cursor = new StreamPendingClaimer.Cursor();
        FutureTask<Void> task = new FutureTask<>(() -> {
            long lastAutoClaimAt = 0L;
            StreamReconnectBackoff backoff = StreamReconnectBackoff.defaults();
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    long now = System.nanoTime();
                    if (policy.enabled() && (lastAutoClaimAt == 0L ||
                            now - lastAutoClaimAt >= policy.interval().toNanos())) {
                        int reclaimed = pendingClaimer.scan(stream, snapshot, policy, cursor,
                                (id, body) -> process(stream, snapshot, id, body, mapper, listener));
                        lastAutoClaimAt = now;
                        if (reclaimed > 0) { backoff.succeeded(); continue; }
                    }
                    Map<StreamMessageId, Map<String, String>> records = stream.readGroup(
                            snapshot.getGroup(), snapshot.getConsumer(), args);
                    if (records != null) {
                        records.forEach((id, body) -> process(stream, snapshot, id, body, mapper, listener));
                    }
                    backoff.succeeded();
                } catch (RuntimeException failure) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Stream polling failed stream={0} consumer={1} exceptionType={2}",
                            snapshot.getStreamKey(), snapshot.getConsumer(), failure.getClass().getName());
                    if (!backoff.pause()) break;
                }
            }
            return null;
        });
        FutureTask<Void> previous = tasks.putIfAbsent(key, task);
        if (previous != null) {
            throw new IllegalStateException("Consumer task already registered: " + key);
        }
        try {
            executor.execute(task);
        } catch (RuntimeException rejected) {
            tasks.remove(key, task);
            throw rejected;
        }
    }

    /** Stop only the selected consumer; other tasks and shared Redis remain alive. */
    public boolean stopConsuming(String streamKey, String group, String consumer) {
        FutureTask<Void> task = tasks.remove(new TaskKey(streamKey, group, consumer));
        return task != null && task.cancel(true);
    }

    private <T> void process(RStream<String, String> stream, StreamConsumerConfig config,
                             StreamMessageId id, Map<String, String> body,
                             Function<Map<String, String>, T> mapper, StreamTaskListener<T> listener) {
        StreamDeliveryProcessor.Outcome outcome = processor.process(stream, config, id, body,
                () -> listener.onMessage(mapper.apply(body), id));
        counter("redis.stream.delivery", "outcome", outcome.name().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean ackQuietly(String streamKey, String group, StreamMessageId id) {
        return ackQuietly(streamKey, group, id, false);
    }

    public boolean ackQuietly(String streamKey, String group, StreamMessageId id, boolean deleteAfterAck) {
        boolean acked = processor.ackManually(streamKey, group, id, deleteAfterAck);
        counter("redis.stream.manual.ack", "result", acked ? "success" : "failed");
        return acked;
    }

    static boolean pauseAfterError() {
        try {
            Thread.sleep(150);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static void validate(StreamConsumerConfig cfg, Duration block) {
        if (cfg.getStreamKey() == null || cfg.getStreamKey().isBlank()
                || cfg.getGroup() == null || cfg.getGroup().isBlank()
                || cfg.getConsumer() == null || cfg.getConsumer().isBlank()) {
            throw new IllegalArgumentException("streamKey/group/consumer must not be blank");
        }
        if (block == null || block.isZero() || block.isNegative()) {
            throw new IllegalArgumentException("block must be a positive Duration");
        }
    }

    private void counter(String metric, String tag, String value) {
        if (meterRegistry != null) {
            // Stable finite outcome labels; never tag arbitrary tenant/message stream names.
            Counter.builder(metric).tag(tag, value).register(meterRegistry).increment();
        }
    }

    @Override
    public void destroy() {
        if (!closed.compareAndSet(false, true)) return;
        tasks.values().forEach(task -> task.cancel(true));
        tasks.clear();
        // DO NOT shutdown redisson: its lifecycle belongs to the auto-configuration or caller.
    }
}
