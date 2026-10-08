package com.agitg.sharedutility.stream;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Stable Redis Stream entry ID independent of Redisson 3.x/4.x package names.
 * Redis uses unsigned 64-bit millisecond and sequence fields. This class
 * intentionally does not recognize Redis command cursors such as '$', '>', '*'.
 * It models an existing entry ID only; it is NOT a replacement for command cursors.
 */
public record RedisStreamId(BigInteger milliseconds, BigInteger sequence)
        implements Comparable<RedisStreamId> {
    private static final BigInteger MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    public RedisStreamId {
        Objects.requireNonNull(milliseconds, "milliseconds");
        Objects.requireNonNull(sequence, "sequence");
        validate(milliseconds, "milliseconds");
        validate(sequence, "sequence");
    }

    private static void validate(BigInteger value, String field) {
        if (value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException(field + " must be an unsigned 64-bit integer");
        }
    }

    public static RedisStreamId parse(String value) {
        if (value == null || value.length() > 41 || !value.matches("[0-9]+-[0-9]+")) {
            throw new IllegalArgumentException("Expected Redis Stream entry ID <millis>-<sequence>");
        }
        int sep = value.indexOf('-');
        return new RedisStreamId(new BigInteger(value.substring(0, sep)),
                new BigInteger(value.substring(sep + 1)));
    }

    /** Interpret Redisson signed long storage as unsigned Redis 64-bit IDs. */
    public static RedisStreamId fromLongBits(long millisBits, long sequenceBits) {
        return parse(Long.toUnsignedString(millisBits) + "-" + Long.toUnsignedString(sequenceBits));
    }

    /** Redisson 4.x currently renders its long ID components as signed decimal.
     * Guard against values beyond Long.MAX_VALUE until the vendor supports unsigned text.
     */
    public long millisecondsSignedLongExact() {
        return signedLongExact(milliseconds, "milliseconds");
    }

    public long sequenceSignedLongExact() {
        return signedLongExact(sequence, "sequence");
    }

    private static long signedLongExact(BigInteger number, String field) {
        try {
            return number.longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(field + " exceeds supported Redisson signed long ID limit", ex);
        }
    }

    public long millisecondsLongBits() {
        return milliseconds.longValue();
    }

    public long sequenceLongBits() {
        return sequence.longValue();
    }

    public String asRedisId() {
        return milliseconds + "-" + sequence;
    }

    @Override public String toString() {
        return asRedisId();
    }

    @Override public int compareTo(RedisStreamId other) {
        Objects.requireNonNull(other, "other");
        int compared = milliseconds.compareTo(other.milliseconds);
        return compared != 0 ? compared : sequence.compareTo(other.sequence);
    }
}
