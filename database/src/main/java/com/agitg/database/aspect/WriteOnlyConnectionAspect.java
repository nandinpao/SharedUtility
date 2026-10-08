package com.agitg.database.aspect;

import com.agitg.database.RoutingDataSource;

/**
 * @deprecated 1.x compatibility facade. The old random-writer behavior is deliberately
 * removed; this facade selects the sole configured writer.
 */
@Deprecated(since = "2.0", forRemoval = false)
public class WriteOnlyConnectionAspect {
    public void setWriteOnly() { RoutingDataSource.markWriteOnlyRandom(); }
    public void clear() { RoutingDataSource.clear(); }
}
