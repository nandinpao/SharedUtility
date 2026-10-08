package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.agitg.redisson.bean.RedissonProperties;

class LegacyRedissonConfigurationBindingTest {

    @Test
    void v1SingleServerPropertiesBindWithoutTlsSettings() {
        var source = new MapConfigurationPropertySource(Map.of(
                "redis.database", "0",
                "redis.timeout", "30000",
                "redis.mode", "single",
                "redis.single.address", "127.0.0.1:6379"));

        var props = new Binder(source)
                .bind("redis", Bindable.of(RedissonProperties.class))
                .orElseThrow(() -> new IllegalStateException("redis binding failed"));
        assertEquals("single", props.getMode());
        assertEquals("127.0.0.1:6379", props.getSingle().getAddress());
        assertNotNull(props.getTls());
        assertFalse(props.getTls().isEnabled());
        assertFalse(props.getTls().isRequired());
    }

    @Test
    void v1ClusterPropertyNamesRemainBindCompatible() {
        var source = new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("redis.mode", "cluster"),
                Map.entry("redis.timeout", "30000"),
                Map.entry("redis.cluster.scan-interval", "1000"),
                Map.entry("redis.cluster.nodes[0]", "127.0.0.1:7001"),
                Map.entry("redis.cluster.read-mode", "SLAVE"),
                Map.entry("redis.cluster.retry-attempts", "3"),
                Map.entry("redis.cluster.slave-connection-pool-size", "64"),
                Map.entry("redis.cluster.master-connection-pool-size", "64"),
                Map.entry("redis.cluster.retry-interval", "1500")));

        var props = new Binder(source)
                .bind("redis", Bindable.of(RedissonProperties.class))
                .orElseThrow(() -> new IllegalStateException("redis binding failed"));
        assertEquals(java.util.List.of("127.0.0.1:7001"), props.getCluster().getNodes());
        assertEquals("SLAVE", props.getCluster().getReadMode());
        assertEquals(64, props.getCluster().getSlaveConnectionPoolSize());
    }
}
