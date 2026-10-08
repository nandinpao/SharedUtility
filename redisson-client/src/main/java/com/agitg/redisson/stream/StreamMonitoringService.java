package com.agitg.redisson.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Fixed-cardinality, operator-approved sampling. Owns only its scheduled task.
 * A -1 gauge indicates sampling failure/unknown, NOT zero pending.
 */
public final class StreamMonitoringService implements AutoCloseable {
    private record Sampled(String stream, String group, AtomicLong pending,
                           AtomicLong dlq, Counter failures) { }
    private final StreamPendingSnapshot snapshot;
    private final List<Sampled> watched;
    private final long intervalMillis;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final System.Logger logger = System.getLogger(StreamMonitoringService.class.getName());

    public StreamMonitoringService(StreamPendingSnapshot snapshot, MeterRegistry registry,
                                   StreamMonitoringProperties properties) {
        this.snapshot = Objects.requireNonNull(snapshot);
        Objects.requireNonNull(registry);
        Objects.requireNonNull(properties).validate();
        if (!properties.isEnabled()) throw new IllegalArgumentException("monitoring must be enabled");
        intervalMillis = properties.getInterval().toMillis();
        List<Sampled> entries = new ArrayList<>();
        for (var watch : properties.getWatch()) {
            String stream = watch.getStream(), group = watch.getGroup();
            AtomicLong pending = new AtomicLong(-1), dlq = new AtomicLong(-1);
            Gauge.builder("redis.stream.pending", pending, AtomicLong::doubleValue)
                    .tag("stream", stream).tag("group", group).register(registry);
            Gauge.builder("redis.stream.dlq.entries", dlq, AtomicLong::doubleValue)
                    .tag("stream", stream).tag("group", group).register(registry);
            Counter failures = Counter.builder("redis.stream.monitoring.failures")
                    .tag("stream", stream).tag("group", group).register(registry);
            entries.add(new Sampled(stream, group, pending, dlq, failures));
        }
        watched = List.copyOf(entries);
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "shareutility-redis-monitor");
            thread.setDaemon(true);
            return thread;
        });
    }
    public void start() {
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(this::refreshSafely, 0, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }
    /** Testable explicit poll; scheduler also calls it. */
    public void refreshSafely() {
        if (executor.isShutdown()) return;
        for (var entry : watched) {
            try {
                var current = snapshot.sample(entry.stream(), entry.group());
                entry.pending().set(current.pending());
                entry.dlq().set(current.deadLetters());
            } catch (RuntimeException error) {
                entry.pending().set(-1L);
                entry.dlq().set(-1L);
                entry.failures().increment();
                logger.log(System.Logger.Level.WARNING,
                        "Redis metrics sample failed stream={0} group={1} exceptionType={2}",
                        entry.stream(), entry.group(), error.getClass().getName());
            }
        }
    }
    @Override public void close() {
        executor.shutdownNow();
    }
}
