package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamMultiReadGroupArgs;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

/** Both PER_STREAM and MULTI_READ modes use the same delivery/ACK/DLQ processor. */
public class StreamGroupConsumerService implements DisposableBean {

    public enum Mode { PER_STREAM, MULTI_READ }
    private record Registration(String stream, String group, String consumer) {}
    private final Set<Registration> ownedRegistrations = new HashSet<>();
    private final RedissonClient redisson;
    private final AutoClaimPolicyResolver policyResolver;
    private final StreamTaskConsumer taskConsumer;
    private final StreamDeliveryProcessor processor;
    private final StreamPendingClaimer pendingClaimer = new StreamPendingClaimer();
    private final List<Registration> ownedSingleTasks = new ArrayList<>();
    private final List<FutureTask<Void>> ownedMultiTasks = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private static final System.Logger LOGGER = System.getLogger(StreamGroupConsumerService.class.getName());

    /** Legacy constructor; manual clients use built-in retry defaults. */
    public StreamGroupConsumerService(RedissonClient redisson, AutoClaimPolicyResolver resolver) {
        this(redisson, resolver, new StreamTaskConsumer(redisson, resolver),
                new StreamDeliveryProcessor(redisson, StreamRetryPolicy.defaults()));
    }

    @Autowired
    public StreamGroupConsumerService(RedissonClient redisson, AutoClaimPolicyResolver resolver,
                                      StreamTaskConsumer taskConsumer, StreamDeliveryProcessor processor) {
        this.redisson = Objects.requireNonNull(redisson);
        this.policyResolver = Objects.requireNonNull(resolver);
        this.taskConsumer = Objects.requireNonNull(taskConsumer);
        this.processor = Objects.requireNonNull(processor);
    }

    public void startConsuming(List<StreamConsumerConfig> configs, Duration block,
                               ExecutorService executor, Consumer<StreamMessageWrapper> callback) {
        startConsuming(configs, block, executor, callback, null, Mode.PER_STREAM);
    }

    public synchronized void startConsuming(List<StreamConsumerConfig> configs, Duration block,
                                           ExecutorService executor, Consumer<StreamMessageWrapper> callback,
                                           @Nullable AutoClaimPolicy overridePolicy, Mode mode) {
        Objects.requireNonNull(configs, "configs");
        // Immutable snapshot of caller-provided mutable beans.
        configs = configs.stream().map(c -> new StreamConsumerConfig(
                c.getStreamKey(), c.getGroup(), c.getConsumer(), c.isAutoAck(), c.isAutoCreateGroup()))
                .toList();
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(mode, "mode");
        if (closed.get()) throw new IllegalStateException("Consumer service is closed");
        if (configs.isEmpty()) throw new IllegalArgumentException("configs is empty");
        configs.forEach(c -> StreamTaskConsumer.validate(c, block));
        if (configs.stream().map(StreamConsumerConfig::getStreamKey).distinct().count() != configs.size()) {
            throw new IllegalArgumentException("duplicate streamKey in one consumer group registration");
        }
        for (StreamConsumerConfig cfg : configs) {
            if (ownedRegistrations.contains(registration(cfg))) {
                throw new IllegalStateException("Consumer already registered for stream/group/consumer");
            }
        }

        if (mode == Mode.PER_STREAM) {
            // A single executor/task for each stream; no duplicate processing implementation.
            List<StreamConsumerConfig> newlyStarted = new ArrayList<>();
            try {
                for (StreamConsumerConfig cfg : configs) {
                    taskConsumer.startConsuming(cfg, block, body -> body, executor,
                            (body, id) -> callback.accept(new StreamMessageWrapper(cfg.getStreamKey(), id, body)),
                            overridePolicy);
                    newlyStarted.add(cfg);
                }
                newlyStarted.forEach(cfg -> ownedSingleTasks.add(registration(cfg)));
                newlyStarted.forEach(cfg -> ownedRegistrations.add(registration(cfg)));
            } catch (RuntimeException failure) {
                newlyStarted.forEach(cfg -> taskConsumer.stopConsuming(
                        cfg.getStreamKey(), cfg.getGroup(), cfg.getConsumer()));
                throw failure;
            }
            return;
        }
        startMultiRead(configs, block, executor, callback, overridePolicy);
        configs.forEach(cfg -> ownedRegistrations.add(registration(cfg)));
    }

    private static Registration registration(StreamConsumerConfig cfg) {
        return new Registration(cfg.getStreamKey(), cfg.getGroup(), cfg.getConsumer());
    }

    private void startMultiRead(List<StreamConsumerConfig> configs, Duration block,
                                ExecutorService executor, Consumer<StreamMessageWrapper> callback,
                                AutoClaimPolicy overridePolicy) {
        String group = configs.get(0).getGroup();
        String consumer = configs.get(0).getConsumer();
        if (configs.stream().anyMatch(cfg -> !group.equals(cfg.getGroup()) || !consumer.equals(cfg.getConsumer()))) {
            throw new IllegalArgumentException("MULTI_READ requires the same group and consumer on all streams");
        }
        Map<String, StreamConsumerConfig> byKey = new LinkedHashMap<>();
        Map<String, RStream<String, String>> streams = new LinkedHashMap<>();
        Map<String, AutoClaimPolicy> policies = new HashMap<>();
        Map<String, StreamPendingClaimer.Cursor> cursors = new HashMap<>();
        Map<String, Long> lastScan = new HashMap<>();
        configs.forEach(cfg -> {
            String key = cfg.getStreamKey();
            byKey.put(key, cfg);
            streams.put(key, redisson.getStream(key));
            policies.put(key, policyResolver.resolve(cfg, overridePolicy));
            cursors.put(key, new StreamPendingClaimer.Cursor());
        });
        String firstKey = configs.get(0).getStreamKey();
        Map<String, StreamMessageId> offsets = new LinkedHashMap<>();
        for (int i = 1; i < configs.size(); i++) {
            offsets.put(configs.get(i).getStreamKey(), StreamMessageId.NEVER_DELIVERED);
        }
        var args = StreamMultiReadGroupArgs.greaterThan(StreamMessageId.NEVER_DELIVERED, offsets)
                .count(Math.max(1, 10 * configs.size())).timeout(block);

        FutureTask<Void> task = new FutureTask<>(() -> {
            StreamReconnectBackoff backoff = StreamReconnectBackoff.defaults();
            while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    long now = System.nanoTime();
                    boolean claimed = false;
                    for (var entry : byKey.entrySet()) {
                        String key = entry.getKey();
                        StreamConsumerConfig cfg = entry.getValue();
                        AutoClaimPolicy p = policies.get(key);
                        long last = lastScan.getOrDefault(key, 0L);
                        if (p.enabled() && (last == 0L || now - last >= p.interval().toNanos())) {
                            int n = pendingClaimer.scan(streams.get(key), cfg, p, cursors.get(key),
                                    (id, body) -> process(streams.get(key), cfg, id, body, callback));
                            lastScan.put(key, now);
                            claimed |= n > 0;
                        }
                    }
                    if (claimed) continue;
                    Map<String, Map<StreamMessageId, Map<String, String>>> results =
                            streams.get(firstKey).readGroup(group, consumer, args);
                    if (results != null) {
                        for (var byStream : results.entrySet()) {
                            StreamConsumerConfig cfg = byKey.get(byStream.getKey());
                            if (cfg != null && byStream.getValue() != null) {
                                byStream.getValue().forEach((id, body) ->
                                        process(streams.get(cfg.getStreamKey()), cfg, id, body, callback));
                            }
                        }
                    }
                    backoff.succeeded();
                } catch (RuntimeException failure) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Multi-stream polling failed group={0} exceptionType={1}",
                            group, failure.getClass().getName());
                    if (!backoff.pause()) break;
                }
            }
            return null;
        });
        ownedMultiTasks.add(task);
        try {
            executor.execute(task);
        } catch (RuntimeException rejected) {
            ownedMultiTasks.remove(task);
            throw rejected;
        }
    }

    private void process(RStream<String, String> stream, StreamConsumerConfig cfg,
                         StreamMessageId id, Map<String, String> body,
                         Consumer<StreamMessageWrapper> callback) {
        processor.process(stream, cfg, id, body,
                () -> callback.accept(new StreamMessageWrapper(cfg.getStreamKey(), id, body)));
    }

    @Override
    public synchronized void destroy() {
        if (!closed.compareAndSet(false, true)) return;
        ownedSingleTasks.forEach(key -> taskConsumer.stopConsuming(
                key.stream(), key.group(), key.consumer()));
        ownedSingleTasks.clear();
        ownedMultiTasks.forEach(f -> f.cancel(true));
        ownedMultiTasks.clear();
        ownedRegistrations.clear();
        // The shared Redisson client remains open for other consumers.
    }
}
