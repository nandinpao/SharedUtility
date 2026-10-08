package com.agitg.sharedutility.database.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PoolCloseSupportTest {
 @Test void closesEveryPoolEvenIfOneFailsAndSuppressesLaterFailures() {
  var events = new ArrayList<Integer>();
  var failure = assertThrows(IllegalStateException.class, () ->
      PoolCloseSupport.closeOwned(List.of(1,2,3), n -> {
       events.add(n);
       if (n == 1 || n == 3) throw new IllegalStateException("failed-"+n);
      }));
  assertEquals(List.of(1,2,3), events);
  assertEquals("failed-1",failure.getMessage());
  assertEquals(1,failure.getSuppressed().length);
  assertEquals("failed-3",failure.getSuppressed()[0].getMessage());
 }
 @Test void startupFailureRemainsPrimary() {
  var original = new IllegalArgumentException("bad-configuration");
  var events = new ArrayList<Integer>();
  PoolCloseSupport.closeAfterFailure(List.of(1,2,3), n -> {
    events.add(n);
    if (n == 2) throw new IllegalStateException("close failure");
  }, original);
  assertEquals(List.of(1,2,3),events);
  assertEquals("bad-configuration",original.getMessage());
  assertEquals("close failure",original.getSuppressed()[0].getMessage());
 }
 @Test void emptyAndNullBoundaries() {
  PoolCloseSupport.closeOwned(List.of(), n -> fail("must not be called"));
  assertThrows(NullPointerException.class, () -> PoolCloseSupport.closeOwned(null, n -> {}));
  assertThrows(NullPointerException.class, () -> PoolCloseSupport.closeOwned(List.of(), null));
 }
}
