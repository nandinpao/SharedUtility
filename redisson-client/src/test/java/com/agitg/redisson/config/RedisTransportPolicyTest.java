package com.agitg.redisson.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedisTransportPolicyTest {
    @Test void plainConnectionForExplicitLocalLegacyUse() {
        assertEquals("redis://localhost:6379", new RedisTransportPolicy(false, false).endpoint("localhost:6379"));
    }
    @Test void tlsUsesRedissScheme() {
        assertEquals("rediss://redis.example.com:6380",
                new RedisTransportPolicy(true, true).endpoint("redis.example.com:6380"));
    }
    @Test void strictPolicyRefusesDowngrade() {
        assertThrows(IllegalStateException.class, () -> new RedisTransportPolicy(false, true));
    }
    @Test void credentialsInAddressRejected() {
        var tls = new RedisTransportPolicy(true, true);
        assertThrows(IllegalStateException.class, () -> tls.endpoint("user:secret@host:6379"));
        assertThrows(IllegalStateException.class, () -> tls.endpoint("redis://host:6379"));
        assertThrows(IllegalStateException.class, () -> tls.endpoint("host:6379/0"));
    }
}
