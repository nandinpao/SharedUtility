package com.agitg.redisson.config;

import com.agitg.redisson.bean.RedissonProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.SslVerificationMode;

import static org.junit.jupiter.api.Assertions.*;

/** Redisson 4.8 centralized authentication and TLS settings. */
class Redisson4ConfigContractTest {
    @Test void existingJsonJackson2CodecRemainsConfigured() {
        var properties = single();
        var config = RedissonClientConfigFactory.create(properties, new ObjectMapper());
        assertInstanceOf(JsonJacksonCodec.class, config.getCodec());
        assertEquals("redis://127.0.0.1:6379", config.useSingleServer().getAddress());
    }

    @Test void passwordIsOnRootConfigNotServerConfig() {
        var properties = single();
        properties.setPassword("test-secret");
        var config = RedissonClientConfigFactory.create(properties, new ObjectMapper());
        assertEquals("test-secret", config.getPassword());
    }

    @Test void tlsIsOnRootConfig() {
        var properties = single();
        properties.getTls().setEnabled(true);
        properties.getTls().setRequired(true);
        var config = RedissonClientConfigFactory.create(properties, new ObjectMapper());
        assertEquals(SslVerificationMode.STRICT, config.getSslVerificationMode());
        assertEquals("rediss://127.0.0.1:6379", config.useSingleServer().getAddress());
    }

    private RedissonProperties single() {
        var properties = new RedissonProperties();
        properties.setMode("single");
        properties.setTimeout(3000);
        properties.setDatabase(0);
        var single = new RedissonProperties.RedissonSingle();
        single.setAddress("127.0.0.1:6379");
        properties.setSingle(single);
        return properties;
    }
}
