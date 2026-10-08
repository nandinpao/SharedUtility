package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisEndpointValidatorTest {
    @Test void singleValid() {
        assertDoesNotThrow(() -> RedisEndpointValidator.validate("single", 3000, 1, "localhost:6379", null));
    }
    @Test void clusterValid() {
        assertDoesNotThrow(() -> RedisEndpointValidator.validate("cluster", 3000, 0,
                null, List.of("redis-1:6379", "redis-2:6379")));
    }
    @Test void missingMode() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate(null, 3000, 0, "localhost:6379", null));
    }
    @Test void invalidMode() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("sentinel", 3000, 0, "localhost:6379", null));
    }
    @Test void invalidTimeout() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("single", 0, 0, "localhost:6379", null));
    }
    @Test void negativeDatabase() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("single", 3000, -1, "localhost:6379", null));
    }
    @Test void clusterMustUseDatabaseZero() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("cluster", 3000, 1, null, List.of("r:6379")));
    }
    @Test void emptyClusterNodes() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("cluster", 3000, 0, null, List.of()));
    }
    @Test void duplicateClusterNodes() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("cluster", 3000, 0, null, List.of("r:6379", "r:6379")));
    }
    @Test void malformedAddress() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("single", 3000, 0, "redis://host:6379", null));
    }
    @Test void invalidPort() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("single", 3000, 0, "host:99999", null));
    }
    @Test void emptyAddress() {
        assertThrows(IllegalStateException.class, () -> RedisEndpointValidator.validate("single", 3000, 0, "", null));
    }
}
