package com.agitg.database;

import java.util.List;

/**
 * Read-only operational diagnostics for the library-owned Hikari pools.
 * Implementations never expose JDBC URLs, usernames, credentials, or SQL.
 * Values are point-in-time observations, not an availability guarantee.
 */
public interface RoutingPoolDiagnostics {
    /** -1 for counters means that the underlying pool has not initialized. */
    record PoolState(String poolName, int active, int idle, int total, int waiting, boolean closed) { }

    List<PoolState> poolStates();
}
