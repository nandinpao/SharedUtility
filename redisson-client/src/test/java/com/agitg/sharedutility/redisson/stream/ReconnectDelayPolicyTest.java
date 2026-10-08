package com.agitg.sharedutility.redisson.stream;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectDelayPolicyTest {
 @Test void preservesLegacyEqualJitter() {
  assertEquals(100,ReconnectDelayPolicy.delayMillis(200,5000,1,0.0));
  assertEquals(200,ReconnectDelayPolicy.delayMillis(200,5000,1,1.0));
  assertEquals(200,ReconnectDelayPolicy.delayMillis(200,5000,2,0.0));
  assertEquals(400,ReconnectDelayPolicy.delayMillis(200,5000,3,0.0));
  assertEquals(2500,ReconnectDelayPolicy.delayMillis(200,5000,31,0.0));
  assertEquals(5000,ReconnectDelayPolicy.delayMillis(200,5000,31,1.0));
 }
 @Test void rejectsNaNAndInvalidBounds() {
  assertThrows(IllegalArgumentException.class,()-> ReconnectDelayPolicy.delayMillis(200,5000,1,Double.NaN));
  assertThrows(IllegalArgumentException.class,()-> ReconnectDelayPolicy.delayMillis(200,5000,1,-0.001));
  assertThrows(IllegalArgumentException.class,()-> ReconnectDelayPolicy.delayMillis(200,5000,1,1.001));
  assertThrows(IllegalArgumentException.class,()-> ReconnectDelayPolicy.delayMillis(0,5000,1,0.5));
  assertThrows(IllegalArgumentException.class,()-> ReconnectDelayPolicy.delayMillis(200,5000,0,0.5));
 }
}
