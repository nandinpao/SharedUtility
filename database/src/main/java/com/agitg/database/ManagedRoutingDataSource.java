package com.agitg.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.agitg.sharedutility.database.core.PoolCloseSupport;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/**
 * Owns only pools constructed by ShareUtility. A consumer-provided datasource
 * must never be closed by this class. Close is idempotent, even if individual
 * Hikari pools fail while shutting down.
 */
final class ManagedRoutingDataSource extends RoutingDataSource
        implements AutoCloseable, RoutingPoolDiagnostics {
    private final List<HikariDataSource> owned;
    private final AtomicBoolean closed = new AtomicBoolean();

    ManagedRoutingDataSource(List<Object> writes, List<Object> reads, Object defaultKey,
                             List<HikariDataSource> owned) {
        super(writes, reads, defaultKey);
        this.owned = List.copyOf(Objects.requireNonNull(owned, "owned"));
    }

    @Override public List<PoolState> poolStates() {
        List<PoolState> result = new ArrayList<>(owned.size());
        for (HikariDataSource pool : owned) {
            HikariPoolMXBean mx = pool.getHikariPoolMXBean();
            boolean isClosed = closed.get() || pool.isClosed();
            result.add(new PoolState(pool.getPoolName(),
                    mx == null ? -1 : mx.getActiveConnections(),
                    mx == null ? -1 : mx.getIdleConnections(),
                    mx == null ? -1 : mx.getTotalConnections(),
                    mx == null ? -1 : mx.getThreadsAwaitingConnection(),
                    isClosed));
        }
        return List.copyOf(result);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        PoolCloseSupport.closeOwned(owned, HikariDataSource::close);
    }

    /** Used by initialization error paths; never masks the original error. */
    static void closeAfterFailure(List<HikariDataSource> pools, Throwable initialFailure) {
        PoolCloseSupport.closeAfterFailure(pools, HikariDataSource::close, initialFailure);
    }
}
