package com.agitg.redisson.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RDeque;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedissonSafeApiTest {
    private final RedissonClient client = mock(RedissonClient.class);
    private final RedissonAccess access = new RedissonAccess(client, new ObjectMapper());

    @Test void durationBucketUsesMillisecondsButLegacyStillUsesSeconds() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(client.getBucket("key")).thenReturn(bucket);
        access.setBucket("key", "value", Duration.ofMillis(1500));
        verify(bucket).set("value", 1500, TimeUnit.MILLISECONDS);
        access.setBucket("key", "value", 2L);
        verify(bucket).set("value", 2L, TimeUnit.SECONDS);
        assertThrows(IllegalArgumentException.class,
                () -> access.setBucket("key", "value", Duration.ZERO));
        verifyNoMoreInteractions(bucket);
    }

    @Test void durationMapAndExpiryRejectInvalidArgumentsBeforeRedisMutation() {
        RMap<Object,Object> map = mock(RMap.class);
        RBucket<Object> bucket = mock(RBucket.class);
        when(client.getMap("map")).thenReturn(map);
        when(client.getBucket("key")).thenReturn(bucket);
        assertThrows(IllegalArgumentException.class,
                () -> access.putMapValue("map", "a", "b", Duration.ZERO));
        verifyNoInteractions(map);
        access.putMapValue("map", "a", "b", Duration.ofSeconds(3));
        verify(map).put("a", "b");
        verify(map).expire(Duration.ofSeconds(3));
        assertThrows(IllegalArgumentException.class,
                () -> access.expireKey("key", Duration.ofNanos(1)));
        verifyNoInteractions(bucket);
        access.expireKey("key", Duration.ofMillis(70));
        verify(bucket).expire(Duration.ofMillis(70));
    }

    @Test void millisecondLockOwnershipAndInterruptContracts() throws Exception {
        RLock lock = mock(RLock.class);
        when(client.getLock("lock")).thenReturn(lock);
        access.lock("lock", Duration.ofMillis(1500));
        verify(lock).lock(1500, TimeUnit.MILLISECONDS);
        when(lock.tryLock(0, 250, TimeUnit.MILLISECONDS)).thenReturn(true);
        assertTrue(access.tryLock("lock", Duration.ZERO, Duration.ofMillis(250)));
        verify(lock).tryLock(0, 250, TimeUnit.MILLISECONDS);
        assertThrows(IllegalArgumentException.class,
                () -> access.tryLock("lock", Duration.ofMillis(-1), Duration.ofSeconds(1)));
        verify(lock, never()).tryLock(-1, 1000, TimeUnit.MILLISECONDS);
        assertFalse(access.unlockIfHeld("lock"));
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        assertTrue(access.unlockIfHeld("lock"));
        verify(lock).unlock();
    }

    @Test void typedSubscriptionsDelegateWithConcretePayloadClassAndId() {
        RTopic topic = mock(RTopic.class);
        when(client.getTopic("events")).thenReturn(topic);
        MessageListener<String> listener = (channel, event) -> { };
        when(topic.addListener(String.class, listener)).thenReturn(17);
        assertEquals(17, access.subscribe("events", String.class, listener));
        verify(topic).addListener(String.class, listener);
        access.unsubscribe("events", 17);
        verify(topic).removeListener(17);
        assertThrows(NullPointerException.class, () -> access.subscribe("events", null, listener));
    }

    @Test void atomicListPollReturnsNullOnEmptyList() {
        RDeque<Object> deque = mock(RDeque.class);
        when(client.getDeque("items")).thenReturn(deque);
        assertNull(access.pollFromList("items", String.class));
        when(deque.pollFirst()).thenReturn(Map.of("id", 7));
        assertEquals(7, access.pollFromList("items", RecordItem.class).id());
        verify(deque, times(2)).pollFirst();
    }

    record RecordItem(int id) { }
}
