package com.agitg.database;

import java.util.List;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class RoutingDataSource extends AbstractRoutingDataSource {

    private final List<Object> readDataSources;
    private final List<Object> writeDataSources;
    private static final ThreadLocal<Boolean> isRead = new ThreadLocal<>();
    private static final ThreadLocal<String> preferredWrite = new ThreadLocal<>();
    private static final ThreadLocal<String> preferredRead = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> forceWritePool = new ThreadLocal<>();

    private final Object defaultKey;
    private int readCounter = 0;
    private int writeCounter = 0;

    public RoutingDataSource(List<Object> writes, List<Object> reads, Object defaultKey) {
        this.writeDataSources = writes;
        this.readDataSources = reads;
        this.defaultKey = defaultKey;
    }

    public static void markReadOnly() {
        isRead.set(true);
    }

    public static void markWrite() {
        isRead.set(false);
    }

    public static void markWriteOnlyRandom() {
        isRead.set(false);
        forceWritePool.set(true);
    }

    public static void setPreferredWrite(String name) {
        preferredWrite.set(name);
    }

    public static void setPreferredRead(String name) {
        preferredRead.set(name);
    }

    public static void clear() {
        isRead.remove();
        preferredWrite.remove();
        preferredRead.remove();
        forceWritePool.remove();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        // if (Boolean.TRUE.equals(isRead.get())) {
        //     if (preferredRead.get() != null) {
        //         return preferredRead.get();
        //     }
        //     int index = (readCounter++) % readDataSources.size();
        //     return readDataSources.get(index);
        // } else {
        //     if (Boolean.TRUE.equals(forceWritePool.get())) {
        //         int index = (writeCounter++) % writeDataSources.size();
        //         return writeDataSources.get(index);
        //     }
        //     if (preferredWrite.get() != null) {
        //         return preferredWrite.get();
        //     }
        //     int index = (writeCounter++) % writeDataSources.size();
        //     return writeDataSources.get(index);
        // }

        if (Boolean.TRUE.equals(isRead.get())) {
            if (preferredRead.get() != null)
                return preferredRead.get();
            int index = (readCounter++) % readDataSources.size();
            return readDataSources.get(index);
        } else if (Boolean.TRUE.equals(forceWritePool.get())) {
            int index = (writeCounter++) % writeDataSources.size();
            return writeDataSources.get(index);
        } else if (preferredWrite.get() != null) {
            return preferredWrite.get();
        }

        return defaultKey;
    }
}
