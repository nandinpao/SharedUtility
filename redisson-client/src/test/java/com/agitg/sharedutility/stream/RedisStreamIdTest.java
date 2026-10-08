package com.agitg.sharedutility.stream;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RedisStreamIdTest {
    @Test void canonicalizesAndOrdersNumerically() {
        assertEquals("9-2", RedisStreamId.parse("0009-0002").asRedisId());
        var list = new java.util.ArrayList<>(List.of(RedisStreamId.parse("10-0"), RedisStreamId.parse("9-11")));
        list.sort(null);
        assertEquals("9-11", list.getFirst().asRedisId());
    }
    @Test void retainsAllUnsigned64BitValuesAcrossLongBits() {
        String max = "18446744073709551615";
        var id = RedisStreamId.parse(max+"-"+max);
        assertEquals(id, RedisStreamId.fromLongBits(id.millisecondsLongBits(), id.sequenceLongBits()));
        assertEquals(max+"-"+max, id.toString());
        assertThrows(IllegalArgumentException.class, id::millisecondsSignedLongExact);
        assertThrows(IllegalArgumentException.class, id::sequenceSignedLongExact);
    }
    @Test void rejectsSpecialCursorsNegativeValuesAndOverflow() {
        for (String bad : List.of("*", ">", "$", "-1-2", "1", "a-1", "1-2 ",
                "18446744073709551616-0", "0-18446744073709551616", "１-2")) {
            assertThrows(IllegalArgumentException.class, () -> RedisStreamId.parse(bad), bad);
        }
        assertThrows(NullPointerException.class, () -> new RedisStreamId(null, BigInteger.ZERO));
    }
    @Test void doesNotConflateDifferentEntries() {
        assertNotEquals(RedisStreamId.parse("1-1"), RedisStreamId.parse("1-2"));
    }
}
