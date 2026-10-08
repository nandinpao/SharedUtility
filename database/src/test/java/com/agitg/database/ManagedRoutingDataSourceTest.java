package com.agitg.database;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.util.List;
import java.util.Map;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedRoutingDataSourceTest {
    @Test void manualRouterInitializationResolvesConfiguredTargetWithoutSpringBeanLifecycle() throws Exception {
        // Unit-level regression for direct factory invocations such as PostgresRoutingIT.
        var pool = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        when(pool.getConnection()).thenReturn(connection);
        var router = new ManagedRoutingDataSource(List.of("writer"), List.of("writer"),
                "writer", List.of(pool));
        router.setTargetDataSources(Map.of("writer", pool));
        router.setDefaultTargetDataSource(pool);
        try {
            router.afterPropertiesSet();
            assertSame(connection, router.getConnection());
        } finally {
            router.close();
        }
        verify(pool, times(1)).close();
    }

    @Test void exposesBoundedReadOnlyMetricsWithoutSecrets() {
        var pool = mock(HikariDataSource.class);
        var mx = mock(HikariPoolMXBean.class);
        when(pool.getPoolName()).thenReturn("pg-main");
        when(pool.getHikariPoolMXBean()).thenReturn(mx);
        when(mx.getActiveConnections()).thenReturn(2);
        when(mx.getIdleConnections()).thenReturn(3);
        when(mx.getTotalConnections()).thenReturn(5);
        when(mx.getThreadsAwaitingConnection()).thenReturn(1);
        var router = new ManagedRoutingDataSource(List.of("writer"), List.of(), "writer", List.of(pool));
        var state = ((RoutingPoolDiagnostics) router).poolStates().getFirst();
        assertEquals("pg-main", state.poolName());
        assertEquals(2, state.active());
        assertEquals(3, state.idle());
        assertEquals(1, state.waiting());
        assertFalse(state.closed());
        assertFalse(state.toString().contains("jdbc:"));
    }

    @Test void unstartedPoolReportsUnknownRatherThanZero() {
        var pool = mock(HikariDataSource.class);
        var router = new ManagedRoutingDataSource(List.of("writer"), List.of(), "writer", List.of(pool));
        assertEquals(-1, router.poolStates().getFirst().active());
        router.close();
        assertTrue(router.poolStates().getFirst().closed());
    }

    @Test void closesOnlyOwnedPoolsExactlyOnce() {
        var first = mock(HikariDataSource.class);
        var second = mock(HikariDataSource.class);
        var router = new ManagedRoutingDataSource(List.of("writer"), List.of("reader"), "writer",
                List.of(first, second));
        router.close();
        router.close();
        verify(first, times(1)).close();
        verify(second, times(1)).close();
    }

    @Test void failedPoolCloseDoesNotPreventOtherPoolsFromClosing() {
        var first = mock(HikariDataSource.class);
        var second = mock(HikariDataSource.class);
        doThrow(new IllegalStateException("first close failed")).when(first).close();
        var router = new ManagedRoutingDataSource(List.of("writer"), List.of(), "writer",
                List.of(first, second));
        assertThrows(IllegalStateException.class, router::close);
        verify(second).close();
        router.close(); // repeat close is safe
        verify(first, times(1)).close();
    }

    @Test void partialInitializationRetainsOriginalFailureAndSuppressesCleanupFailure() {
        var first = mock(HikariDataSource.class);
        doThrow(new IllegalStateException("cleanup")).when(first).close();
        var original = new IllegalArgumentException("failed to build second pool");
        ManagedRoutingDataSource.closeAfterFailure(List.of(first), original);
        assertEquals(1, original.getSuppressed().length);
        assertEquals("cleanup", original.getSuppressed()[0].getMessage());
    }
}
