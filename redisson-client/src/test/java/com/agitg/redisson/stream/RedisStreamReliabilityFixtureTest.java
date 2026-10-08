package com.agitg.redisson.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** A failed @BeforeEach must never cause a second cleanup failure. */
class RedisStreamReliabilityFixtureTest {
    @Test
    void cleanupAfterInitializationFailureIsNullSafe() {
        assertDoesNotThrow(() -> new RedisStreamReliabilityIT().cleanup());
    }
}
