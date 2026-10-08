package com.agitg.redisson.stream;

import com.agitg.sharedutility.stream.RedisStreamId;
import org.junit.jupiter.api.Test;
import org.redisson.api.stream.StreamMessageId;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Redisson 4.8 API contract: portable IDs must not depend on vendor package paths. */
class Redisson4MigrationContractTest {
    @Test void portableIdRoundTrip() {
        var item = new StreamMessageWrapper("orders", new StreamMessageId(1700000000000L, 3L), Map.of("k", "v"));
        assertEquals(RedisStreamId.parse("1700000000000-3"), item.getPortableMessageId());
        item.setPortableMessageId(RedisStreamId.parse("1700000000001-4"));
        assertEquals(new StreamMessageId(1700000000001L, 4L), item.getMessageId());
    }

    @Test void missingIdRemainsMissing() {
        var item = new StreamMessageWrapper();
        assertNull(item.getPortableMessageId());
        item.setPortableMessageId(RedisStreamId.parse("123-7"));
        item.setPortableMessageId(null);
        assertNull(item.getMessageId());
    }

    @Test void unsignedIdAboveVendorLongLimitIsRejected() {
        var item = new StreamMessageWrapper();
        assertThrows(IllegalArgumentException.class,
                () -> item.setPortableMessageId(RedisStreamId.parse("18446744073709551615-0")));
    }
}
