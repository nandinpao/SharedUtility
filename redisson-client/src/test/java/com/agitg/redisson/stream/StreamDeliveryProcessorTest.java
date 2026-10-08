package com.agitg.redisson.stream;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.PendingEntry;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StreamDeliveryProcessorTest {
    private final StreamMessageId id = new StreamMessageId(100, 0);
    private final StreamConsumerConfig cfg = new StreamConsumerConfig("orders", "g", "c", true, false);
    private RedissonClient redis;
    private RStream<String, String> source;
    private RStream<String, String> dlq;
    private StreamDeliveryProcessor processor;

    @SuppressWarnings("unchecked")
    @BeforeEach void prepare() {
        redis = mock(RedissonClient.class);
        source = mock(RStream.class);
        dlq = mock(RStream.class);
        when(redis.<String,String>getStream("orders")).thenReturn(source);
        when(redis.<String,String>getStream("orders:dlq")).thenReturn(dlq);
        processor = new StreamDeliveryProcessor(redis, new StreamRetryPolicy(3, ":dlq"));
    }

    @Test void successAckOneMeansSuccess() {
        when(source.ack("g", id)).thenReturn(1L);
        assertEquals(StreamDeliveryProcessor.Outcome.ACKED,
                processor.process(source, cfg, id, Map.of("a", "b"), () -> {}));
    }
    @Test void ackZeroIsNotSuccess() {
        when(source.ack("g", id)).thenReturn(0L);
        assertEquals(StreamDeliveryProcessor.Outcome.ACK_FAILED,
                processor.process(source, cfg, id, Map.of(), () -> {}));
    }
    @Test void transientAckExceptionRetriesAndSucceeds() {
        when(source.ack("g", id)).thenThrow(new IllegalStateException("offline")).thenReturn(1L);
        assertEquals(StreamDeliveryProcessor.Outcome.ACKED,
                processor.process(source, cfg, id, Map.of(), () -> {}));
        verify(source, times(2)).ack("g", id);
    }
    @Test void permanentlyFailedAckIsNotReportedAsSuccess() {
        when(source.ack("g", id)).thenThrow(new IllegalStateException("offline"));
        assertEquals(StreamDeliveryProcessor.Outcome.ACK_FAILED,
                processor.process(source, cfg, id, Map.of(), () -> {}));
        verify(source, times(3)).ack("g", id);
    }
    @Test void manualModeNeverAutoAcks() {
        cfg.setAutoAck(false);
        assertEquals(StreamDeliveryProcessor.Outcome.ACK_DEFERRED,
                processor.process(source, cfg, id, Map.of(), () -> {}));
        verify(source, never()).ack(any(), any(StreamMessageId.class));
    }
    @Test void temporaryHandlerFailureLeavesPendingWithoutAck() {
        when(source.listPending("g", id, id, 1)).thenReturn(List.of(pending(1)));
        assertEquals(StreamDeliveryProcessor.Outcome.LEFT_PENDING,
                processor.process(source, cfg, id, Map.of(), () -> { throw new IllegalArgumentException(); }));
        verifyNoInteractions(dlq);
        verify(source, never()).ack(any(), any(StreamMessageId.class));
    }
    @Test void attemptsAtThresholdWritesDlqBeforeAck() {
        when(source.listPending("g", id, id, 1)).thenReturn(List.of(pending(3)));
        when(dlq.add(any(StreamAddArgs.class))).thenReturn(new StreamMessageId(200, 0));
        when(source.ack("g", id)).thenReturn(1L);
        assertEquals(StreamDeliveryProcessor.Outcome.DEAD_LETTERED,
                processor.process(source, cfg, id, Map.of("secret", "example"), () -> {throw new IllegalStateException();}));
        var inOrder = inOrder(dlq, source);
        inOrder.verify(dlq).add(any(StreamAddArgs.class));
        inOrder.verify(source).ack("g", id);
    }
    @Test void dlqFailureMustLeaveSourceUnacked() {
        when(source.listPending("g", id, id, 1)).thenReturn(List.of(pending(3)));
        when(dlq.add(any(StreamAddArgs.class))).thenThrow(new IllegalStateException("offline"));
        assertEquals(StreamDeliveryProcessor.Outcome.DLQ_FAILED,
                processor.process(source, cfg, id, Map.of(), () -> {throw new IllegalStateException();}));
        verify(source, never()).ack(any(), any(StreamMessageId.class));
    }
    @Test void missingPelEntryFailsClosed() {
        when(source.listPending("g", id, id, 1)).thenReturn(List.of());
        assertEquals(StreamDeliveryProcessor.Outcome.LEFT_PENDING,
                processor.process(source, cfg, id, Map.of(), () -> {throw new IllegalStateException();}));
        verifyNoInteractions(dlq);
    }
    @Test void pelReadFailureFailsClosed() {
        when(source.listPending("g", id, id, 1)).thenThrow(new IllegalStateException("timeout"));
        assertEquals(StreamDeliveryProcessor.Outcome.LEFT_PENDING,
                processor.process(source, cfg, id, Map.of(), () -> {throw new IllegalStateException();}));
        verifyNoInteractions(dlq);
    }
    @Test void manualAckZeroNeverDeletesOriginal() {
        when(source.ack("g", id)).thenReturn(0L);
        assertFalse(processor.ackManually("orders", "g", id, true));
        verify(source, never()).remove(any(StreamMessageId.class));
    }
    @Test void manualAckSuccessDeletesOriginalIfRequested() {
        when(source.ack("g", id)).thenReturn(1L);
        assertTrue(processor.ackManually("orders", "g", id, true));
        verify(source).remove(id);
    }
    @Test void dlqAckFailureIsNotReportedAsDeadLettered() {
        when(source.listPending("g", id, id, 1)).thenReturn(List.of(pending(3)));
        when(dlq.add(any(StreamAddArgs.class))).thenReturn(new StreamMessageId(201, 0));
        when(source.ack("g", id)).thenReturn(0L);
        assertEquals(StreamDeliveryProcessor.Outcome.ACK_FAILED,
                processor.process(source, cfg, id, Map.of(), () -> {throw new IllegalStateException();}));
    }
    private PendingEntry pending(long deliveries) {
        return new PendingEntry(id, "c", 1, deliveries);
    }
}
