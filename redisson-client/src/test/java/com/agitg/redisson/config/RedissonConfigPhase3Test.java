package com.agitg.redisson.config;

import org.junit.jupiter.api.Test;
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
        assertEquals("rediss://localhost:6380", config.getSingleServerConfig().getAddress());
        assertEquals("JsonJacksonCodec", config.getCodec().getClass().getSimpleName());
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
