package com.agitg.redisson.stream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.redisson.api.stream.AutoClaimResult;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamMessageId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.TimeUnit;

class StreamPendingClaimerTest {
    private final StreamConsumerConfig cfg = new StreamConsumerConfig("orders", "g", "c", true, false);
    private final AutoClaimPolicy policy = new AutoClaimPolicy(true, Duration.ofMillis(10), Duration.ofMillis(10), 1, 3);

    @SuppressWarnings("unchecked")
    @Test void emptyPageWithNextCursorMustContinue() {
        RStream<String,String> stream = mock(RStream.class);
        StreamMessageId next = new StreamMessageId(20, 0);
        StreamMessageId id = new StreamMessageId(21, 0);
        when(stream.autoClaim("g", "c", 10, TimeUnit.MILLISECONDS, StreamMessageId.MIN, 1))
                .thenReturn(new AutoClaimResult<>(next, Map.of(), List.of()));
        when(stream.autoClaim("g", "c", 10, TimeUnit.MILLISECONDS, next, 1))
                .thenReturn(new AutoClaimResult<>(StreamMessageId.MIN, Map.of(id, Map.of("k", "v")), List.of()));
        var seen = new ArrayList<StreamMessageId>();
        var cursor = new StreamPendingClaimer.Cursor();
        assertEquals(1, new StreamPendingClaimer().scan(stream, cfg, policy, cursor,
                (messageId, body) -> seen.add(messageId)));
        assertEquals(List.of(id), seen);
        assertEquals(StreamMessageId.MIN, cursor.next());
        verify(stream, times(2)).autoClaim(eq("g"), eq("c"), eq(10L), eq(TimeUnit.MILLISECONDS), any(), eq(1));
    }
    @SuppressWarnings("unchecked")
    @Test void maxRoundsPersistsCursorAcrossTicks() {
        RStream<String,String> stream = mock(RStream.class);
        var p = new AutoClaimPolicy(true, Duration.ofMillis(10), Duration.ofMillis(10), 1, 1);
        StreamMessageId next = new StreamMessageId(20, 0);
        when(stream.autoClaim("g", "c", 10, TimeUnit.MILLISECONDS, StreamMessageId.MIN, 1))
                .thenReturn(new AutoClaimResult<>(next, Map.of(), List.of()));
        when(stream.autoClaim("g", "c", 10, TimeUnit.MILLISECONDS, next, 1))
                .thenReturn(new AutoClaimResult<>(StreamMessageId.MIN, Map.of(), List.of()));
        var cursor = new StreamPendingClaimer.Cursor();
        var claimer = new StreamPendingClaimer();
        assertEquals(0, claimer.scan(stream, cfg, p, cursor, (id, body) -> {}));
        assertEquals(next, cursor.next());
        assertEquals(0, claimer.scan(stream, cfg, p, cursor, (id, body) -> {}));
        assertEquals(StreamMessageId.MIN, cursor.next());
    }
    @SuppressWarnings("unchecked")
    @Test void nullResultLeavesNoClaims() {
        RStream<String,String> stream = mock(RStream.class);
        assertEquals(0, new StreamPendingClaimer().scan(stream, cfg, policy,
                new StreamPendingClaimer.Cursor(), (id, body) -> fail("should not be called")));
    }
    @SuppressWarnings("unchecked")
    @Test void disabledPolicyNeverUsesRedis() {
        RStream<String,String> stream = mock(RStream.class);
        var disabled = AutoClaimPolicy.disabled();
        assertEquals(0, new StreamPendingClaimer().scan(stream, cfg, disabled,
                new StreamPendingClaimer.Cursor(), (id, body) -> {}));
        verifyNoInteractions(stream);
    }
}
