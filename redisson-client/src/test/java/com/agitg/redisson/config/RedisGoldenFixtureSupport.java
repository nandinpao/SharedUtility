package com.agitg.redisson.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.redisson.codec.JsonJacksonCodec;
import com.agitg.redisson.bean.RedissonProperties;
import io.netty.buffer.ByteBuf;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

/** Stable corpus. Do not change names/DTO FQCN after sealing Phase 3.8 fixtures. */
final class RedisGoldenFixtureSupport {
    private RedisGoldenFixtureSupport() { }

    static final String DEFAULT = "redisson-3.50-default";
    static final String CONFIGURED = "shareutility-phase3_8";

    record Sample(String id, String channel, Object value) { }

    public static class GoldenFixturePojo {
        private String id;
        private int count;
        private List<String> tags;
        public GoldenFixturePojo() { }
        public GoldenFixturePojo(String id, int count, List<String> tags) {
            this.id = id;
            this.count = count;
            this.tags = tags;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        @Override public boolean equals(Object o) {
            return o instanceof GoldenFixturePojo other && count == other.count
                    && java.util.Objects.equals(id, other.id)
                    && java.util.Objects.equals(tags, other.tags);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, count, tags); }
    }

    static List<Sample> samples() {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", 42L);
        map.put("name", "phase38");
        var nested = new LinkedHashMap<String, Object>();
        nested.put("items", new ArrayList<>(List.of("a", "b")));
        nested.put("active", true);
        return List.of(
                new Sample("bucket-string", "value", "hello-old-consumer"),
                new Sample("bucket-long-64bit", "value", 9_223_372_036_854_775_000L),
                new Sample("bucket-map-mutable", "value", map),
                new Sample("bucket-list-mutable", "value", new ArrayList<>(List.of("x", "y"))),
                new Sample("bucket-nested-map", "value", nested),
                new Sample("bucket-pojo", "value", new GoldenFixturePojo("A-100", 7, new ArrayList<>(List.of("x", "y")))),
                new Sample("hash-key", "map-key", "field-A"),
                new Sample("hash-value", "map-value", new GoldenFixturePojo("H-100", 3, new ArrayList<>(List.of("hash")))),
                new Sample("stream-key", "map-key", "event"),
                new Sample("stream-value", "map-value", new LinkedHashMap<>(Map.of("eventId", "evt-123")))
        );
    }

    static JsonJacksonCodec codec(String variant) {
        if (DEFAULT.equals(variant)) return new JsonJacksonCodec();
        if (CONFIGURED.equals(variant)) {
            return new JsonJacksonCodec(new RedissonAutoConfig(new RedissonProperties()).redissonObjectMapper());
        }
        throw new IllegalArgumentException("Unknown codec variant: " + variant);
    }

    static Encoder encoder(JsonJacksonCodec codec, String channel) {
        return switch (channel) {
            case "value" -> codec.getValueEncoder();
            case "map-key" -> codec.getMapKeyEncoder();
            case "map-value" -> codec.getMapValueEncoder();
            default -> throw new IllegalArgumentException(channel);
        };
    }

    static Decoder<Object> decoder(JsonJacksonCodec codec, String channel) {
        return switch (channel) {
            case "value" -> codec.getValueDecoder();
            case "map-key" -> codec.getMapKeyDecoder();
            case "map-value" -> codec.getMapValueDecoder();
            default -> throw new IllegalArgumentException(channel);
        };
    }

    static byte[] encode(JsonJacksonCodec codec, Sample sample) throws IOException {
        ByteBuf buf = encoder(codec, sample.channel()).encode(sample.value());
        try {
            byte[] raw = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), raw);
            return raw;
        } finally { buf.release(); }
    }

    static Object decode(JsonJacksonCodec codec, String channel, byte[] bytes) throws IOException {
        ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(bytes);
        try { return decoder(codec, channel).decode(buffer, null); }
        finally { buffer.release(); }
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static String row(String variant, Sample sample, byte[] raw) {
        return variant + '\t' + sample.id() + '\t' + sample.channel() + '\t'
                + sha256(raw) + '\t' + Base64.getEncoder().encodeToString(raw);
    }
}
