package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.Map;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.PendingResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamMonitoringServiceTest {
    private StreamMonitoringProperties enabled() {
        var p = new StreamMonitoringProperties();
        p.setEnabled(true);p.setInterval(Duration.ofSeconds(5));
        var w = new StreamMonitoringProperties.Watch();
        w.setStream("orders");w.setGroup("g");p.setWatch(java.util.List.of(w));
        return p;
    }
    @Test void policyRejectsUnboundedCardinality() {
        var props = enabled(); props.setMaxWatchedStreams(0);
        assertThrows(IllegalArgumentException.class, props::validate);
        props = enabled(); props.setInterval(Duration.ofMillis(100));
        assertThrows(IllegalArgumentException.class, props::validate);
    }
    @Test void closeMarksGaugesUnknownAndRejectsRestart() {
        var redis = mock(RedissonClient.class);
        var registry = new SimpleMeterRegistry();
        var monitor = new StreamMonitoringService(new StreamPendingSnapshot(redis, ":dlq"), registry, enabled());
        monitor.close();
        monitor.close();
        assertEquals(-1d, registry.get("redis.stream.pending").tag("stream", "orders").tag("group", "g").gauge().value());
        assertThrows(IllegalStateException.class, monitor::start);
    }
    @SuppressWarnings("unchecked")
    @Test void exportsRealSnapshotAndShowsUnknownOnFailure() {
        var redis = mock(RedissonClient.class);
        RStream<String,String> src = mock(RStream.class);
        RStream<String,String> dead = mock(RStream.class);
        when(redis.<String,String>getStream("orders")).thenReturn(src);
        when(redis.<String,String>getStream("orders:dlq")).thenReturn(dead);
        when(src.getPendingInfo("g")).thenReturn(new PendingResult(6, null, null, Map.of()));
        when(dead.size()).thenReturn(2L);
        var registry = new SimpleMeterRegistry();
        try (var monitor = new StreamMonitoringService(new StreamPendingSnapshot(redis, ":dlq"),
                registry, enabled())) {
            monitor.refreshSafely();
            assertEquals(6d, registry.get("redis.stream.pending").tag("stream", "orders").tag("group", "g").gauge().value());
            assertEquals(2d, registry.get("redis.stream.dlq.entries").tag("stream", "orders").tag("group", "g").gauge().value());
            when(src.getPendingInfo("g")).thenThrow(new IllegalStateException("offline"));
            monitor.refreshSafely();
            assertEquals(-1d, registry.get("redis.stream.pending").tag("stream", "orders").tag("group", "g").gauge().value());
            assertEquals(1d, registry.get("redis.stream.monitoring.failures").counter().count());
        }
    }
}
