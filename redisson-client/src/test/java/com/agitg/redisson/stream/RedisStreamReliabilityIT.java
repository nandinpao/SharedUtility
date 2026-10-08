package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.redisson.Redisson;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/** Actual Redis 7.4 integration: invoked ONLY by Maven -Predis-it with Docker. */
@Execution(ExecutionMode.SAME_THREAD)
class RedisStreamReliabilityIT {
    private static GenericContainer<?> container;
    // Fault-injection tests kill connections or pause Redis. A static client would
    // carry that broken connection state into the next test method.
    private RedissonClient redis;
    private final AutoClaimPolicy policy = new AutoClaimPolicy(true,
            Duration.ofMillis(120), Duration.ofMillis(65), 10, 3);
    private ExecutorService executor;
    private StreamTaskConsumer consumer;
    private StreamGroupConsumerService service;
    private String streamKey;
    private String group;

    @BeforeAll static void startRedis() {
        container = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379);
        container.start();
    }
    @AfterAll static void stopRedis() {
        if (container != null) container.stop();
    }
    @BeforeEach void prepare() {
        // Redis itself is shared to limit container startup cost, but a Redisson
        // client belongs to ONE test. Previous CLIENT KILL/PAUSE tests must not
        // poison the connection pool of subsequent test methods.
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + container.getHost() + ":" + container.getMappedPort(6379))
                .setConnectTimeout(10_000)
                .setTimeout(10_000); // Docker Desktop CI only; production defaults untouched.
        config.setCodec(new JsonJacksonCodec());
        redis = Redisson.create(config);
        executor = Executors.newFixedThreadPool(3);
        String unique = UUID.randomUUID().toString().replace("-", "");
        streamKey = "qa:stream:" + unique;
        group = "qa-group-" + unique;
        RStream<String, String> stream = redis.getStream(streamKey);
        stream.add(StreamAddArgs.entry("seed", "start"));
        stream.createGroup(StreamCreateGroupArgs.name(group).id(StreamMessageId.NEWEST));
        var resolver = new AutoClaimPolicyResolver(new AutoClaimProperties());
        var processor = new StreamDeliveryProcessor(redis, new StreamRetryPolicy(3, ":dlq"));
        consumer = new StreamTaskConsumer(redis, resolver, processor);
        service = new StreamGroupConsumerService(redis, resolver, consumer, processor);
    }
    @AfterEach void cleanup() {
        // @BeforeEach may fail on its first XADD (e.g. Redis is temporarily
        // paused). Teardown must not replace that error with an NPE and must
        // always release whichever resources were initialized successfully.
        try {
            if (service != null) service.destroy();
        } finally {
            try {
                if (consumer != null) consumer.destroy();
            } finally {
                try {
                    if (executor != null) {
                        executor.shutdownNow();
                        try {
                            // Do not leak a polling worker into the next test.
                            executor.awaitTermination(2, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } finally {
                    if (redis != null) redis.shutdown();
                }
            }
        }
    }

    @Test void successfulCallbackAcknowledgesAndRemovesFromPel() throws Exception {
        var cfg = cfg("consumer-a");
        CountDownLatch invoked = new CountDownLatch(1);
        consumer.startConsuming(cfg, Duration.ofMillis(150), v -> v, executor,
                (body, id) -> invoked.countDown(), policy);
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("v", "ok"));
        assertTrue(invoked.await(6, TimeUnit.SECONDS));
        await(() -> pendingCount(id) == 0, Duration.ofSeconds(6));
    }

    @Test void failedCallbackRedeliversThenAcknowledges() throws Exception {
        var cfg = cfg("consumer-a");
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);
        consumer.startConsuming(cfg, Duration.ofMillis(150), v -> v, executor,
                (body, id) -> {
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("transient");
                    completed.countDown();
                }, policy);
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("v", "ok"));
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        await(() -> pendingCount(id) == 0, Duration.ofSeconds(6));
        assertEquals(2, attempts.get());
    }

    @Test void poisonMessageMovesToDlqOnThirdDelivery() throws Exception {
        var cfg = cfg("consumer-a");
        AtomicInteger attempts = new AtomicInteger();
        consumer.startConsuming(cfg, Duration.ofMillis(120), v -> v, executor,
                (body, id) -> { attempts.incrementAndGet(); throw new IllegalStateException("poison"); }, policy);
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("v", "bad"));
        await(() -> redis.<String,String>getStream(streamKey + ":dlq").size() > 0, Duration.ofSeconds(12));
        await(() -> pendingCount(id) == 0, Duration.ofSeconds(6));
        assertEquals(3, attempts.get());
        assertEquals(1L, redis.<String,String>getStream(streamKey + ":dlq").size());
    }

    @Test void manualAckModeMustKeepPelUntilExplicitAck() throws Exception {
        var cfg = cfg("consumer-a");
        cfg.setAutoAck(false);
        CountDownLatch processed = new CountDownLatch(1);
        consumer.startConsuming(cfg, Duration.ofMillis(120), v -> v, executor,
                (body, id) -> processed.countDown(), AutoClaimPolicy.disabled());
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("v", "manual"));
        assertTrue(processed.await(6, TimeUnit.SECONDS));
        await(() -> pendingCount(id) == 1, Duration.ofSeconds(6));
        assertTrue(consumer.ackQuietly(streamKey, group, id));
        assertFalse(consumer.ackQuietly(streamKey, group, id));
        assertEquals(0, pendingCount(id));
    }

    @Test void consumerRestartRecoversPendingFromAnotherConsumer() throws Exception {
        var cfg = cfg("consumer-a");
        CountDownLatch failed = new CountDownLatch(1);
        consumer.startConsuming(cfg, Duration.ofMillis(120), v -> v, executor,
                (body, id) -> { failed.countDown(); throw new IllegalStateException("worker stopped"); },
                new AutoClaimPolicy(false, Duration.ofMillis(120), Duration.ofMillis(65), 10, 1));
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("v", "recover"));
        assertTrue(failed.await(6, TimeUnit.SECONDS));
        assertTrue(consumer.stopConsuming(streamKey, group, "consumer-a"));
        CountDownLatch recovered = new CountDownLatch(1);
        consumer.startConsuming(cfg("consumer-b"), Duration.ofMillis(120), v -> v, executor,
                (body, receivedId) -> recovered.countDown(), policy);
        assertTrue(recovered.await(10, TimeUnit.SECONDS));
        await(() -> pendingCount(id) == 0, Duration.ofSeconds(6));
    }

    @Test void multiReadConsumesTwoStreamsUsingSharedAckProcessor() throws Exception {
        String stream2 = streamKey + "-second";
        RStream<String, String> next = redis.getStream(stream2);
        next.add(StreamAddArgs.entry("seed", "start"));
        next.createGroup(StreamCreateGroupArgs.name(group).id(StreamMessageId.NEWEST));
        CountDownLatch received = new CountDownLatch(2);
        service.startConsuming(List.of(cfg("consumer-a"),
                        new StreamConsumerConfig(stream2, group, "consumer-a", true, false)),
                Duration.ofMillis(150), executor, wrapper -> received.countDown(), policy,
                StreamGroupConsumerService.Mode.MULTI_READ);
        StreamMessageId firstId = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("x", "one"));
        StreamMessageId secondId = next.add(StreamAddArgs.entry("x", "two"));
        assertTrue(received.await(10, TimeUnit.SECONDS));
        await(() -> pendingCount(firstId) == 0 && next.listPending(group, secondId, secondId, 1).isEmpty(),
                Duration.ofSeconds(6));
    }

    @Test void killedClientConnectionsRecoverWithoutDroppingNewDelivery() throws Exception {
        var cfg = cfg("consumer-a");
        CountDownLatch received = new CountDownLatch(1);
        consumer.startConsuming(cfg, Duration.ofMillis(120), v -> v, executor,
                (body, id) -> received.countDown(), policy);
        // Kills the TCP connections of normal Redis clients, not the Redis server.
        // At-least-once delivery must recover after Redisson reconnects.
        container.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "normal", "SKIPME", "yes");
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("eventType", "retry"));
        assertTrue(received.await(15, TimeUnit.SECONDS));
        await(() -> pendingCount(id) == 0, Duration.ofSeconds(6));
    }

    @Test void operatorSnapshotReflectsRealPendingAndDlq() throws Exception {
        var cfg = cfg("consumer-a");
        cfg.setAutoAck(false);
        CountDownLatch received = new CountDownLatch(1);
        consumer.startConsuming(cfg, Duration.ofMillis(120), v -> v, executor,
                (body, id) -> received.countDown(), AutoClaimPolicy.disabled());
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("eventType", "audit"));
        assertTrue(received.await(6, TimeUnit.SECONDS));
        var snapshot = new StreamPendingSnapshot(redis, ":dlq");
        await(() -> snapshot.sample(streamKey, group).pending() == 1, Duration.ofSeconds(6));
        assertEquals(0, snapshot.sample(streamKey, group).deadLetters());
        assertTrue(consumer.ackQuietly(streamKey, group, id));
        await(() -> snapshot.sample(streamKey, group).pending() == 0, Duration.ofSeconds(6));
    }

    @Test void temporaryServerPauseEventuallyRecoversAndAcknowledges() throws Exception {
        var cfg = cfg("consumer-a");
        CountDownLatch received = new CountDownLatch(1);
        consumer.startConsuming(cfg, Duration.ofMillis(150), v -> v, executor,
                (body, id) -> received.countDown(), policy);
        // An actual Redis CLIENT PAUSE, not a network partition / process crash.
        var response = container.execInContainer("redis-cli", "CLIENT", "PAUSE", "500", "ALL");
        assertEquals(0, response.getExitCode());
        StreamMessageId id = redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("v", "paused"));
        assertTrue(received.await(10, TimeUnit.SECONDS));
        await(() -> pendingCount(id) == 0, Duration.ofSeconds(6));
    }

    @Test void stoppingConsumersDoesNotCloseSharedRedisson() {
        service.destroy();
        consumer.destroy();
        assertFalse(redis.isShutdown());
        assertNotNull(redis.<String,String>getStream(streamKey).add(StreamAddArgs.entry("alive", "true")));
    }

    private StreamConsumerConfig cfg(String consumerName) {
        return new StreamConsumerConfig(streamKey, group, consumerName, true, false);
    }
    private int pendingCount(StreamMessageId id) {
        return redis.<String,String>getStream(streamKey).listPending(group, id, id, 1).size();
    }
    private static void await(BooleanSupplier condition, Duration limit) throws InterruptedException {
        long end = System.nanoTime() + limit.toNanos();
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(50);
        }
        fail("Condition not satisfied within " + limit);
    }
}
