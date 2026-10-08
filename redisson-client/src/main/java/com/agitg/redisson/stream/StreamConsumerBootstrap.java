package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Owns only its task and any executor it explicitly creates. */
public class StreamConsumerBootstrap<T> implements AutoCloseable {
    private final StreamTaskConsumer streamTask;
    private final StreamGroupEnsurer groupEnsurer;
    private final StreamConsumerConfig config;
    private final Duration block;
    private final Function<Map<String, String>, T> mapper;
    private final ExecutorService executor;
    private final StreamTaskListener<T> listener;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ExecutorService created;

    public StreamConsumerBootstrap(StreamTaskConsumer task, StreamGroupEnsurer groupEnsurer,
                                   StreamConsumerConfig config, Duration block,
                                   Function<Map<String, String>, T> mapper,
                                   ExecutorService executor, StreamTaskListener<T> listener) {
        this.streamTask=task;this.groupEnsurer=groupEnsurer;this.config=config;
        this.block=block;this.mapper=mapper;this.executor=executor;this.listener=listener;
    }
    public static <T> StreamConsumerBootstrapBuilder<T> builder() { return new StreamConsumerBootstrapBuilder<>(); }
    public static final class StreamConsumerBootstrapBuilder<T> {
        private StreamTaskConsumer task;
        private StreamGroupEnsurer ensurer;
        private StreamConsumerConfig config;
        private Duration block;
        private Function<Map<String,String>,T> mapper;
        private ExecutorService executor;
        private StreamTaskListener<T> listener;
        public StreamConsumerBootstrapBuilder<T> streamTask(StreamTaskConsumer v) { task=v;return this; }
        public StreamConsumerBootstrapBuilder<T> groupEnsurer(StreamGroupEnsurer v) { ensurer=v;return this; }
        public StreamConsumerBootstrapBuilder<T> config(StreamConsumerConfig v) { config=v;return this; }
        public StreamConsumerBootstrapBuilder<T> block(Duration v) { block=v;return this; }
        public StreamConsumerBootstrapBuilder<T> mapper(Function<Map<String,String>,T> v) { mapper=v;return this; }
        public StreamConsumerBootstrapBuilder<T> executor(ExecutorService v) { executor=v;return this; }
        public StreamConsumerBootstrapBuilder<T> listener(StreamTaskListener<T> v) { listener=v;return this; }
        public StreamConsumerBootstrap<T> build() {
            return new StreamConsumerBootstrap<>(task, ensurer, config, block, mapper, executor, listener);
        }
    }
    public void start() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Bootstrap already started");
        try {
            if (config.isAutoCreateGroup()) groupEnsurer.ensureGroup(config.getStreamKey(), config.getGroup());
            ExecutorService exec = executor != null ? executor
                    : (created = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "redis-stream-" + config.getConsumer());
                        t.setDaemon(true);
                        return t;
                    }));
            streamTask.startConsuming(config, block, mapper, exec, listener);
        } catch (RuntimeException e) {
            if (created != null) created.shutdownNow();
            started.set(false);
            throw e;
        }
    }
    @Override public void close() {
        if (started.compareAndSet(true, false)) {
            streamTask.stopConsuming(config.getStreamKey(), config.getGroup(), config.getConsumer());
        }
        if (created != null) created.shutdownNow();
    }
}
