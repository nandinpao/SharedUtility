package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.redisson.codec.JsonJacksonCodec;

import com.agitg.redisson.bean.RedissonProperties;

import io.netty.buffer.ByteBuf;

class RedisCodecBackwardCompatibilityTest {

    @Test
    void legacyDefaultCodecPayloadCanBeReadByV2Codec() throws IOException {
        var legacy = new JsonJacksonCodec();
        var current = currentCodec();
        var value = new LegacyPojo("A-100", 7, List.of("x", "y"));

        Object decoded = roundTripAcross(legacy, current, value);
        assertEquals(value, decoded);
    }

    @Test
    void v2PayloadForLegacyCompatiblePojoCanBeReadByLegacyCodec() throws IOException {
        var legacy = new JsonJacksonCodec();
        var current = currentCodec();
        var value = new LegacyPojo("B-200", 9, List.of("p", "q"));

        Object decoded = roundTripAcross(current, legacy, value);
        assertEquals(value, decoded);
    }

    @Test
    void legacyReadableMutableMapAndLongShapesRemainCrossReadable() throws IOException {
        var legacy = new JsonJacksonCodec();
        var current = currentCodec();

        // Compatibility samples must first be readable by the 1.x codec itself.
        // Map.of()/List.of() use final JDK immutable implementation classes and are
        // not a valid raw-Object JsonJacksonCodec compatibility baseline because
        // Redisson may omit polymorphic type metadata for the final runtime class.
        var map = new LinkedHashMap<String, Object>();
        map.put("id", 42L);
        map.put("name", "demo");
        assertEquals(map, roundTripAcross(legacy, legacy, map));
        assertEquals(map, roundTripAcross(legacy, current, map));

        long value = 9_223_372_036_854_775_000L;
        assertEquals(value, roundTripAcross(legacy, legacy, value));
        assertEquals(value, roundTripAcross(current, legacy, value));
    }

    private static JsonJacksonCodec currentCodec() {
        var autoConfig = new RedissonAutoConfig(new RedissonProperties());
        return new JsonJacksonCodec(autoConfig.redissonObjectMapper());
    }

    private static Object roundTripAcross(JsonJacksonCodec writer, JsonJacksonCodec reader, Object value)
            throws IOException {
        ByteBuf buffer = writer.getValueEncoder().encode(value);
        try {
            return reader.getValueDecoder().decode(buffer, null);
        } finally {
            buffer.release();
        }
    }

    static class LegacyPojo {
        private String id;
        private int count;
        private List<String> tags;

        LegacyPojo() { }
        LegacyPojo(String id, int count, List<String> tags) {
            this.id = id;
            this.count = count;
            this.tags = tags;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LegacyPojo p)) return false;
            return count == p.count && Objects.equals(id, p.id) && Objects.equals(tags, p.tags);
        }
        @Override public int hashCode() { return Objects.hash(id, count, tags); }
    }
}
