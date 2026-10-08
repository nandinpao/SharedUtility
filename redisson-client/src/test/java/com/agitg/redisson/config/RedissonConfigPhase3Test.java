package com.agitg.redisson.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.SslVerificationMode;
import com.agitg.redisson.bean.RedissonProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class RedissonConfigPhase3Test {
    private RedissonProperties props() {
        RedissonProperties p = new RedissonProperties();
        p.setMode("single"); p.setTimeout(3000); p.setDatabase(0);
        var single = new RedissonProperties.RedissonSingle();
        single.setAddress("localhost:6380"); p.setSingle(single);
        return p;
    }
    @Test void codecAndTlsGeneratedTogether() {
        var props = props();
        props.getTls().setEnabled(true); props.getTls().setRequired(true);
        var config = RedissonClientConfigFactory.create(props, new ObjectMapper());
        assertEquals("rediss://localhost:6380", config.useSingleServer().getAddress());
        assertEquals(SslVerificationMode.STRICT, config.useSingleServer().getSslVerificationMode());
        assertEquals("JsonJacksonCodec", config.getCodec().getClass().getSimpleName());
    }
    @Test void clusterTlsUsesStrictVerificationOnClusterConfig() {
        RedissonProperties p = new RedissonProperties();
        p.setMode("cluster"); p.setTimeout(3000); p.setDatabase(0);
        p.getTls().setEnabled(true); p.getTls().setRequired(true);
        var cluster = new RedissonProperties.RedissonCluster();
        cluster.setNodes(java.util.List.of("redis-1:6379", "redis-2:6379"));
        cluster.setScanInterval(1000);
        cluster.setRetryAttempts(3);
        cluster.setRetryInterval(1000);
        cluster.setSlaveConnectionPoolSize(16);
        cluster.setMasterConnectionPoolSize(16);
        cluster.setReadMode("MASTER_SLAVE");
        p.setCluster(cluster);

        var config = RedissonClientConfigFactory.create(p, new ObjectMapper());
        assertEquals(SslVerificationMode.STRICT, config.useClusterServers().getSslVerificationMode());
        assertTrue(config.useClusterServers().getNodeAddresses().stream()
                .allMatch(address -> address.startsWith("rediss://")));
    }

    @Test void tlsRequiredCannotBeTurnedOff() {
        var props = props(); props.getTls().setRequired(true);
        assertThrows(IllegalStateException.class, () ->
                RedissonClientConfigFactory.create(props, new ObjectMapper()));
    }
    @Test void invalidModePreventsStartup() {
        var props = props(); props.setMode("invalid");
        assertThrows(IllegalStateException.class, () ->
                RedissonClientConfigFactory.create(props, new ObjectMapper()));
    }
}
