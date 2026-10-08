package com.agitg.redisson.config;

import java.time.Duration;
import com.agitg.sharedutility.redisson.core.DurationMillisPolicy;

/** Legacy package-private facade: keep method descriptors intact. */
final class RedisTimeArguments {
    private RedisTimeArguments() { }
    static long positiveMillis(Duration duration, String name) {
        return DurationMillisPolicy.positiveMillis(duration, name);
    }
    static long nonNegativeMillis(Duration duration, String name) {
        return DurationMillisPolicy.nonNegativeMillis(duration, name);
    }
}
