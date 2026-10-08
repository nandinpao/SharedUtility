package com.agitg.database.aspect;

import com.agitg.database.RoutingDataSource;
import com.agitg.database.annotation.Slave;

/** @deprecated 1.x compatibility facade; not an active Aspect. */
@Deprecated(since = "2.0", forRemoval = false)
public class SlaveConnectionAspect {
    public void useSlave(Slave slave) {
        RoutingDataSource.markReadOnly();
        if (slave != null) { RoutingDataSource.setPreferredRead(slave.value()); }
    }
    public void clearSlave(Slave slave) { RoutingDataSource.clear(); }
}
