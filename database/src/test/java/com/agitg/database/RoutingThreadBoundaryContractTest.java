package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Regression guard: ThreadLocal route is neither automatically propagated nor leaked. */
class RoutingThreadBoundaryContractTest {
    @AfterEach void cleanup() { RoutingDataSource.clear(); }

    @Test void existingPooledThreadDoesNotInheritCallersReadRoute() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            try (var scope = RoutingDataSource.readScope(null)) {
                assertEquals(RoutingDataSource.Mode.READ, RoutingDataSource.currentRoute().mode());
                assertEquals(RoutingDataSource.Mode.WRITE, pool.submit(() ->
                        RoutingDataSource.currentRoute().mode()).get(5, TimeUnit.SECONDS));
            }
            assertEquals(RoutingDataSource.Mode.WRITE, RoutingDataSource.currentRoute().mode());
        }
    }

    @Test void virtualThreadDoesNotInheritOrLeakReadRoute() throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            try (var scope = RoutingDataSource.readScope(null)) {
                assertEquals(RoutingDataSource.Mode.WRITE,
                        pool.submit(() -> RoutingDataSource.currentRoute().mode()).get(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test void executorThreadRouteIsRestoredAfterScopeEnds() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            assertEquals(RoutingDataSource.Mode.READ, pool.submit(() -> {
                try (var scope = RoutingDataSource.readScope(null)) {
                    return RoutingDataSource.currentRoute().mode();
                }
            }).get(5, TimeUnit.SECONDS));
            assertEquals(RoutingDataSource.Mode.WRITE,
                    pool.submit(() -> RoutingDataSource.currentRoute().mode()).get(5, TimeUnit.SECONDS));
        }
    }
}
