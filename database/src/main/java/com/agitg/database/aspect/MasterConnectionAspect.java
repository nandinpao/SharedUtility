package com.agitg.database.aspect;

import com.agitg.database.RoutingDataSource;
import com.agitg.database.annotation.Master;

/**
 * @deprecated 1.x compatibility facade. Routing advice is now provided exclusively by
 * {@link RoutingConnectionAspect}; this class is intentionally not an Aspect.
 */
@Deprecated(since = "2.0", forRemoval = false)
public class MasterConnectionAspect {
    public void useMaster(Master master) {
        RoutingDataSource.markWrite();
        if (master != null && !master.value().isBlank()) {
            RoutingDataSource.setPreferredWrite(master.value());
        }
    }
    public void clearMaster(Master master) { RoutingDataSource.clear(); }
}
