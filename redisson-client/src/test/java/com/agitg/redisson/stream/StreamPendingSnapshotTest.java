package com.agitg.redisson.stream;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.redisson.api.PendingResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamPendingSnapshotTest {
    @SuppressWarnings("unchecked")
    @Test void reportsPendingAndDlqCounts() {
        var client = mock(RedissonClient.class);
        RStream<String, String> source = mock(RStream.class);
        RStream<String, String> dlq = mock(RStream.class);
        when(client.<String,String>getStream("orders")).thenReturn(source);
        when(client.<String,String>getStream("orders:dlq")).thenReturn(dlq);
        when(source.getPendingInfo("g")).thenReturn(new PendingResult(7, null, null, Map.of()));
        when(dlq.size()).thenReturn(2L);
        var sampled = new StreamPendingSnapshot(client, ":dlq").sample("orders", "g");
        assertEquals(7L, sampled.pending());
        assertEquals(2L, sampled.deadLetters());
    }
    @SuppressWarnings("unchecked")
    @Test void absentPendingDataDoesNotReportHealthyZero() {
        var client = mock(RedissonClient.class);
        RStream<String, String> source = mock(RStream.class);
        when(client.<String,String>getStream("orders")).thenReturn(source);
        assertThrows(IllegalStateException.class, () ->
                new StreamPendingSnapshot(client, ":dlq").sample("orders", "g"));
    }
}
