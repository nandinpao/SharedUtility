package com.agitg.redisson.stream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL multi-transaction + concurrent duplicate integration: -Predis-it. */
class StreamJdbcIdempotencyIT {
    private static GenericContainer<?> postgres;
    private static PGSimpleDataSource data;
    @BeforeAll static void start() throws Exception {
        postgres = new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withEnv("POSTGRES_DB", "testdb")
                .withEnv("POSTGRES_USER", "test")
                .withEnv("POSTGRES_PASSWORD", "test")
                .withExposedPorts(5432);
        postgres.start();
        data = new PGSimpleDataSource();
        data.setServerNames(new String[]{postgres.getHost()});
        data.setPortNumbers(new int[]{postgres.getMappedPort(5432)});
        data.setDatabaseName("testdb");
        data.setUser("test");data.setPassword("test");
        String migration = Files.readString(Path.of("..", "docs", "sql",
                "V20261007_01__shareutility_stream_delivery_log.sql"));
        try (Connection cx = data.getConnection(); var statement = cx.createStatement()) {
            statement.execute(migration);
            statement.execute("CREATE TABLE qa_business_effect (operation_key TEXT PRIMARY KEY, amount INT NOT NULL)");
        }
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    @Test void duplicateDoesNotRepeatBusinessInsert() throws Exception {
        var guard = new JdbcStreamIdempotencyGuard(data);
        String id = "100-0";
        assertTrue(guard.executeOnce("qa:normal", "main", id, cx -> write(cx, "payment", 3)));
        assertFalse(guard.executeOnce("qa:normal", "main", id, cx -> write(cx, "payment", 3)));
        assertEquals(1, effects("payment"));
    }
    @Test void failureRollsBackLedgerAndCanBeRetried() throws Exception {
        var guard = new JdbcStreamIdempotencyGuard(data);
        assertThrows(SQLException.class, () -> guard.executeOnce("qa:failure", "main", "200-0",
                cx -> { write(cx, "retry-ok", 1); throw new SQLException("simulate rollback"); }));
        assertEquals(0, effects("retry-ok"));
        assertTrue(guard.executeOnce("qa:failure", "main", "200-0", cx -> write(cx, "retry-ok", 1)));
        assertEquals(1, effects("retry-ok"));
    }
    @Test void concurrentDuplicateWithSameIdentityRunsOnlyOnce() throws Exception {
        var start = new CountDownLatch(1);
        var completions = new AtomicInteger();
        var wins = new AtomicInteger();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                tasks.add(ex.submit(() -> {
                    try {
                        start.await();
                        boolean first = new JdbcStreamIdempotencyGuard(data).executeOnce("qa:race", "main", "300-0",
                                cx -> { write(cx, "only-once", 5); try { Thread.sleep(100); }
                                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt();
                                        throw new SQLException("interrupted", interrupted); } });
                        if (first) wins.incrementAndGet();
                        completions.incrementAndGet();
                    } catch (Throwable exception) { failure.compareAndSet(null, exception); }
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        if (failure.get()!=null) throw new AssertionError("concurrent duplicate failed", failure.get());
        assertEquals(2, completions.get());
        assertEquals(1, wins.get());
        assertEquals(1, effects("only-once"));
    }
    private static void write(Connection cx, String key, int amount) throws SQLException {
        try (var stmt = cx.prepareStatement("INSERT INTO qa_business_effect(operation_key, amount) VALUES (?, ?)")) {
            stmt.setString(1, key);stmt.setInt(2, amount);stmt.executeUpdate();
        }
    }
    private static int effects(String key) throws SQLException {
        try (var cx=data.getConnection();var stmt=cx.prepareStatement(
                "SELECT COUNT(*) FROM qa_business_effect WHERE operation_key=?")) {
            stmt.setString(1,key);
            try (var rs=stmt.executeQuery()) { rs.next();return rs.getInt(1); }
        }
    }
}
