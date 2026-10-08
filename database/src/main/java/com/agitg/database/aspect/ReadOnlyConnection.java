package com.agitg.database.aspect;

import com.agitg.database.RoutingDataSource;

/** @deprecated 1.x compatibility facade; not an active Aspect. */
@Deprecated(since = "2.0", forRemoval = false)
public class ReadOnlyConnection {
    public void setReadOnly() { RoutingDataSource.markReadOnly(); }
    public void clear() { RoutingDataSource.clear(); }
}
