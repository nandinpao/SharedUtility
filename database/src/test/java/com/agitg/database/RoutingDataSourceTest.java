package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RoutingDataSourceTest {
    private static final class InspectableRouter extends RoutingDataSource {
        InspectableRouter(List<Object> writes, List<Object> reads, Object defaultKey) {
            super(writes, reads, defaultKey);
        }
        Object selected() { return determineCurrentLookupKey(); }
    }
    private final InspectableRouter ds = new InspectableRouter(List.of("writer"), List.of("reader1", "reader2"), "writer");

    @AfterEach void cleanup() {
        RoutingDataSource.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test void defaultUsesWriter() { assertEquals("writer", ds.selected()); }

    @Test void nestedScopesRestoreOuterReadAndCleanup() {
        try (var read = RoutingDataSource.readScope("reader1")) {
            assertEquals("reader1", ds.selected());
            try (var write = RoutingDataSource.writeScope(null)) {
                assertEquals("writer", ds.selected());
            }
            assertEquals("reader1", ds.selected());
        }
        assertEquals("writer", ds.selected());
    }

    @Test void exceptionStillRestoresOuter() {
        try (var outer = RoutingDataSource.readScope("reader2")) {
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = RoutingDataSource.writeScope(null)) {
                    throw new IllegalStateException("simulated service failure");
                }
            });
            assertEquals("reader2", ds.selected());
        }
        assertEquals("writer", ds.selected());
    }

    @Test void rejectsScopeMisuseOutOfOrder() {
        var outer = RoutingDataSource.readScope(null);
        var inner = RoutingDataSource.writeScope(null);
        assertThrows(IllegalStateException.class, outer::close);
        inner.close();
        outer.close();
        assertThrows(IllegalStateException.class, outer::close);
    }

    @Test void rejectRouteChangeDuringTransaction() {
        try (var read = RoutingDataSource.readScope("reader1")) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThrows(IllegalStateException.class, () -> RoutingDataSource.writeScope(null));
            assertThrows(IllegalStateException.class, () -> RoutingDataSource.readScope("reader2"));
            try (var same = RoutingDataSource.readScope("reader1")) {
                assertEquals("reader1", ds.selected());
            }
            assertEquals("reader1", ds.selected());
        }
        assertEquals("writer", ds.selected());
    }

    @Test void activeAmbientTransactionOnlyAcceptsDefaultWriter() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (var same = RoutingDataSource.writeScope(null)) {
            assertEquals("writer", ds.selected());
        }
        assertThrows(IllegalStateException.class, () -> RoutingDataSource.readScope(null));
    }

    @Test void unknownTargetFailsClosed() {
        try (var scope = RoutingDataSource.readScope("missing")) {
            assertThrows(IllegalStateException.class, ds::selected);
        }
        try (var scope = RoutingDataSource.writeScope("missing")) {
            assertThrows(IllegalStateException.class, ds::selected);
        }
    }

    @Test void readWithoutReplicaFallsBackToSingleWriter() {
        var single = new InspectableRouter(List.of("single"), List.of(), "single");
        try (var read = RoutingDataSource.readScope(null)) {
            assertEquals("single", single.selected());
        }
    }

    @Test void readReplicaRoundRobin() {
        try (var read = RoutingDataSource.readScope(null)) {
            assertEquals("reader1", ds.selected());
            assertEquals("reader2", ds.selected());
            assertEquals("reader1", ds.selected());
        }
    }

    @Test void threadsHaveIsolatedScopes() throws Exception {
        AtomicReference<Object> other = new AtomicReference<>();
        try (var read = RoutingDataSource.readScope("reader2")) {
            Thread thread = new Thread(() -> {
                other.set(ds.selected());
                try (var scope = RoutingDataSource.readScope("reader1")) {
                    assertEquals("reader1", ds.selected());
                }
            });
            thread.start();
            thread.join();
            assertEquals("writer", other.get());
            assertEquals("reader2", ds.selected());
        }
    }

    @Test void refusesMultipleWritersAtConstruction() {
        assertThrows(IllegalArgumentException.class, () ->
                new InspectableRouter(List.of("writer1", "writer2"), List.of(), "writer1"));
        assertThrows(IllegalArgumentException.class, () ->
                new InspectableRouter(List.of("writer"), List.of("reader"), "reader"));
    }

    @Test void legacyWriteOnlyIsDeterministicAndCannotReplaceScope() {
        RoutingDataSource.markWriteOnlyRandom();
        assertEquals("writer", ds.selected());
        try (var scoped = RoutingDataSource.readScope("reader1")) {
            assertThrows(IllegalStateException.class, RoutingDataSource::markWrite);
            RoutingDataSource.clear();
            assertEquals("reader1", ds.selected());
        }
        assertEquals("writer", ds.selected());
    }
}
