package com.agitg.sharedutility.redisson.core;

import java.time.Duration;
import java.util.Objects;

/** Version-neutral duration validation shared by Redisson 3.x and 4.x facades. */
public final class DurationMillisPolicy {
    private DurationMillisPolicy() { }

    public static long positiveMillis(Duration duration, String name) {
        long millis = millis(duration, name);
        if (millis <= 0) {
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        }
        return millis;
    }
    public static long nonNegativeMillis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return millis(duration, name);
    }
    private static long millis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        try { return duration.toMillis(); }
        catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " exceeds supported millisecond range", overflow);
        }
    }
}
