package com.agitg.database;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes a JDBC connection only when it is first acquired. An open Spring
 * transaction retains its physical connection; changing a lookup key does
 * NOT switch the already bound connection.
 *
 * Prefer scoped {@link #readScope(String)} / {@link #writeScope(String)} to
 * legacy manual setters and clear calls. Scoped state is restored in strict LIFO order.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    public enum Mode { READ, WRITE }

    public record Route(Mode mode, String preferred) {
        public Route {
            Objects.requireNonNull(mode, "mode");
            preferred = preferred == null || preferred.isBlank() ? null : preferred.trim();
        }
    }

    private static final Route DEFAULT_WRITE = new Route(Mode.WRITE, null);
    private static final ThreadLocal<Deque<Route>> SCOPED = new ThreadLocal<>();
    private static final ThreadLocal<Route> LEGACY = new ThreadLocal<>();
    private final List<Object> readDataSources;
    private final List<Object> writeDataSources;
    private final Object defaultKey;
    private final AtomicInteger readCounter = new AtomicInteger();

    public RoutingDataSource(List<Object> writes, List<Object> reads, Object defaultKey) {
        this.writeDataSources = writes == null ? List.of() : List.copyOf(writes);
        this.readDataSources = reads == null ? List.of() : List.copyOf(reads);
        if (this.writeDataSources.size() != 1) {
            throw new IllegalArgumentException("Exactly one write datasource is supported; use a database HA endpoint");
        }
        if (!this.writeDataSources.contains(defaultKey)) {
            throw new IllegalArgumentException("Default datasource must be the configured writer");
        }
        this.defaultKey = Objects.requireNonNull(defaultKey, "defaultKey");
        // No silent fallback for invalid preferred names.
        setLenientFallback(false);
    }

    public static Scope readScope(String preferredReader) {
        return openScope(new Route(Mode.READ, preferredReader));
    }

    public static Scope writeScope(String preferredWriter) {
        return openScope(new Route(Mode.WRITE, preferredWriter));
    }

    private static Scope openScope(Route proposed) {
        assertTransactionCompatible(proposed);
        Deque<Route> stack = SCOPED.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            SCOPED.set(stack);
        }
        stack.push(proposed);
        return new Scope(stack, proposed, Thread.currentThread());
    }

    public static final class Scope implements AutoCloseable {
        private final Deque<Route> stack;
        private final Route route;
        private final Thread owner;
        private boolean closed;

        private Scope(Deque<Route> stack, Route route, Thread owner) {
            this.stack = stack;
            this.route = route;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("Routing scope has already been closed");
            }
            if (Thread.currentThread() != owner || SCOPED.get() != stack || stack.peek() != route) {
                throw new IllegalStateException("Routing scopes must close in LIFO order on the owning thread");
            }
            stack.pop();
            closed = true;
            if (stack.isEmpty()) {
                SCOPED.remove();
            }
        }
    }

    /** For tests and diagnostics, not a promise that the JDBC connection has switched. */
    public static Route currentRoute() {
        Deque<Route> stack = SCOPED.get();
        if (stack != null && !stack.isEmpty()) {
            return stack.peek();
        }
        Route legacy = LEGACY.get();
        return legacy == null ? DEFAULT_WRITE : legacy;
    }

    private static void assertTransactionCompatible(Route proposed) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !currentRoute().equals(proposed)) {
            throw new IllegalStateException("Cannot change datasource route inside an active transaction. "
                    + "Move the route annotation to the outer service boundary before @Transactional; "
                    + "current=" + currentRoute() + ", requested=" + proposed);
        }
    }

    /** @deprecated Use {@link #readScope(String)} with try-with-resources. */
    @Deprecated(since = "2.0", forRemoval = false)
    public static void markReadOnly() { setLegacy(new Route(Mode.READ, null)); }

    /** @deprecated Use {@link #writeScope(String)} with try-with-resources. */
    @Deprecated(since = "2.0", forRemoval = false)
    public static void markWrite() { setLegacy(DEFAULT_WRITE); }

    /** @deprecated No more random multi-writer routing; selects the sole configured writer. */
    @Deprecated(since = "2.0", forRemoval = false)
    public static void markWriteOnlyRandom() { setLegacy(DEFAULT_WRITE); }

    /** @deprecated Set via {@link #writeScope(String)}. */
    @Deprecated(since = "2.0", forRemoval = false)
    public static void setPreferredWrite(String name) { setLegacy(new Route(Mode.WRITE, name)); }

    /** @deprecated Set via {@link #readScope(String)}. */
    @Deprecated(since = "2.0", forRemoval = false)
    public static void setPreferredRead(String name) { setLegacy(new Route(Mode.READ, name)); }

    private static void setLegacy(Route route) {
        if (SCOPED.get() != null) {
            throw new IllegalStateException("Legacy route setters cannot be used inside a routing scope");
        }
        assertTransactionCompatible(route);
        LEGACY.set(route);
    }

    /** Clears only legacy manual routing; never destroys an enclosing AOP scope. */
    public static void clear() { LEGACY.remove(); }

    @Override
    protected Object determineCurrentLookupKey() {
        Route route = currentRoute();
        if (route.mode() == Mode.WRITE) {
            if (route.preferred() != null) {
                return requireKey(route.preferred(), writeDataSources, "write");
            }
            return defaultKey;
        }
        if (route.preferred() != null) {
            return requireKey(route.preferred(), readDataSources, "read");
        }
        if (readDataSources.isEmpty()) {
            return defaultKey; // Single datasource / no replicas: all reads use the writer.
        }
        return readDataSources.get(Math.floorMod(readCounter.getAndIncrement(), readDataSources.size()));
    }

    private Object requireKey(String preferred, List<Object> available, String role) {
        if (!available.contains(preferred)) {
            throw new IllegalStateException("Unknown " + role + " datasource: " + preferred + ", available=" + available);
        }
        return preferred;
    }
}
