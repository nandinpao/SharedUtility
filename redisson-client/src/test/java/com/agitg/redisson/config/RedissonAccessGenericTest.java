package com.agitg.redisson.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedissonAccessGenericTest {
    record Sample(int id, String symbol) { }

    @SuppressWarnings("unchecked")
    @Test void objectCollectionsRehydrateToRequestedConcreteTypes() {
        var redis = mock(RedissonClient.class);
        var bucket = mock(RBucket.class);
        when(redis.getBucket("v")).thenReturn(bucket);
        var access = new RedissonAccess(redis, new ObjectMapper());

        when(bucket.get()).thenReturn(List.of(Map.of("id", 2, "symbol", "TSMC")));
        List<Sample> list = access.getListFromBucket("v", Sample.class);
        assertEquals(2, list.get(0).id());

        when(bucket.get()).thenReturn(Map.of("key", Map.of("id", 3, "symbol", "MSFT")));
        Map<String, Sample> map = access.getMapFromBucket("v", String.class, Sample.class);
        assertEquals("MSFT", map.get("key").symbol());

        when(bucket.get()).thenReturn(Set.of(Map.of("id", 4, "symbol", "NVDA")));
        Set<Sample> set = access.getSetFromBucket("v", Sample.class);
        assertEquals(4, set.iterator().next().id());
    }
}
