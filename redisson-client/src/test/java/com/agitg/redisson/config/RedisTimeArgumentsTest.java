package com.agitg.redisson.config;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedisTimeArgumentsTest {
    @Test void acceptsMillisecondsAndNonNegativeWait() {
        assertEquals(1500, RedisTimeArguments.positiveMillis(Duration.ofMillis(1500), "ttl"));
        assertEquals(1, RedisTimeArguments.positiveMillis(Duration.ofNanos(1_000_000), "ttl"));
        assertEquals(0, RedisTimeArguments.nonNegativeMillis(Duration.ZERO, "wait"));
    }
    @Test void rejectsZeroNegativeSubmillisecondAndOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> RedisTimeArguments.positiveMillis(Duration.ZERO, "ttl"));
        assertThrows(IllegalArgumentException.class,
                () -> RedisTimeArguments.positiveMillis(Duration.ofNanos(999_999), "ttl"));
        assertThrows(IllegalArgumentException.class,
                () -> RedisTimeArguments.positiveMillis(Duration.ofMillis(-1), "ttl"));
        assertThrows(IllegalArgumentException.class,
                () -> RedisTimeArguments.nonNegativeMillis(Duration.ofNanos(-1), "wait"));
        assertThrows(IllegalArgumentException.class,
                () -> RedisTimeArguments.positiveMillis(Duration.ofSeconds(Long.MAX_VALUE), "ttl"));
        assertThrows(NullPointerException.class,
                () -> RedisTimeArguments.positiveMillis(null, "ttl"));
    }
}
