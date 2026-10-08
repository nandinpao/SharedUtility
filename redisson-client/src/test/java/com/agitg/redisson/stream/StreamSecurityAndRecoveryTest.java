package com.agitg.redisson.stream;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamSecurityAndRecoveryTest {
    @Test void dlqIsMetadataOnlyByDefault() {
        assertTrue(StreamDeadLetterPolicy.metadataOnly().sanitizedBody(
                Map.of("account", "secret", "token", "must-not-save")).isEmpty());
    }
    @Test void onlyApprovedFieldsAreSaved() {
        var policy = new StreamDeadLetterPolicy(Set.of("eventType"), 1, 10);
        assertEquals(Map.of("body.eventType", "UPSERT"),
                policy.sanitizedBody(Map.of("eventType", "UPSERT", "password", "s3cr3t")));
    }
    @Test void oversizeFieldsAreDroppedNotTruncated() {
        var policy = new StreamDeadLetterPolicy(Set.of("code"), 1, 4);
        assertTrue(policy.sanitizedBody(Map.of("code", "12345")).isEmpty());
    }
    @Test void rejectsInvalidAllowlistAndPolicyBounds() {
        assertThrows(IllegalArgumentException.class, () -> new StreamDeadLetterPolicy(Set.of("token[]"), 2, 20));
        assertThrows(IllegalArgumentException.class, () -> new StreamDeadLetterPolicy(Set.of("a", "b"), 1, 20));
        assertThrows(IllegalArgumentException.class, () -> new StreamDeadLetterPolicy(Set.of(), 1, 0));
    }
    @Test void duplicatedAllowlistRejectedBeforeBeanCreation() {
        var props = new StreamDeadLetterProperties();
        props.setAllowedFields(List.of("eventType", "eventType"));
        assertThrows(IllegalStateException.class, props::toPolicy);
    }
    @Test void metadataOnlyCannotBeReplayedWithoutSourceRetention() {
        var meta = Map.of("sourceStream", "orders", "sourceGroup", "g", "sourceMessageId", "1-0");
        assertThrows(IllegalStateException.class, () -> StreamReplayPlanner.prepare(meta));
    }
    @Test void replayRequiresDurableIdempotencyIdentity() {
        var data = Map.of("sourceStream", "orders", "sourceGroup", "g",
                "sourceMessageId", "42-0", "body.eventType", "UPSERT");
        var plan = StreamReplayPlanner.prepare(data);
        assertEquals("orders|g|42-0", plan.idempotencyKey());
        assertEquals(Map.of("eventType", "UPSERT"), plan.payload());
        assertThrows(UnsupportedOperationException.class, () -> plan.payload().put("secret", "x"));
    }
    @Test void replayRejectsMalformedOriginalId() {
        assertThrows(IllegalArgumentException.class, () -> StreamReplayPlanner.prepare(Map.of(
                "sourceStream", "orders", "sourceGroup", "g", "sourceMessageId", "not-an-id",
                "body.eventType", "UPSERT")));
    }
    @Test void reconnectBackoffIsCappedAndResetsOnRecovery() {
        var retry = new StreamReconnectBackoff(200, 1600);
        assertEquals(100, retry.delayMillis(0.0));
        assertEquals(400, retry.delayMillis(1.0));
        assertEquals(800, retry.delayMillis(1.0));
        assertEquals(1600, retry.delayMillis(1.0));
        assertEquals(1600, retry.delayMillis(1.0));
        retry.succeeded();
        assertEquals(0, retry.failures());
        assertEquals(200, retry.delayMillis(1.0));
    }
    @Test void reconnectJitterOutsideBoundIsRejected() {
        var retry = StreamReconnectBackoff.defaults();
        assertThrows(IllegalArgumentException.class, () -> retry.delayMillis(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> retry.delayMillis(-0.1));
    }
}
