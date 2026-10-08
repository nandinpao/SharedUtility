package com.agitg.sharedutility.redisson.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class DurationMillisPolicyTest {
 @Test void positiveAndNonnegativeBoundaries() {
  assertEquals(1, DurationMillisPolicy.positiveMillis(Duration.ofMillis(1),"ttl"));
  assertEquals(0, DurationMillisPolicy.nonNegativeMillis(Duration.ZERO,"wait"));
  assertEquals(0, DurationMillisPolicy.nonNegativeMillis(Duration.ofNanos(1),"wait"));
  assertEquals(42,DurationMillisPolicy.positiveMillis(Duration.ofMillis(42),"ttl"));
 }
 @Test void protectsPrecisionAndOverflow() {
  assertThrows(IllegalArgumentException.class,()-> DurationMillisPolicy.positiveMillis(Duration.ofNanos(999999),"ttl"));
  assertThrows(IllegalArgumentException.class,()-> DurationMillisPolicy.positiveMillis(Duration.ofSeconds(Long.MAX_VALUE),"ttl"));
  assertThrows(IllegalArgumentException.class,()-> DurationMillisPolicy.nonNegativeMillis(Duration.ofMillis(-1),"wait"));
  assertThrows(NullPointerException.class,()-> DurationMillisPolicy.nonNegativeMillis(null,"wait"));
 }
}
