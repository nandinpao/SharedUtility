package com.agitg.redisson.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RQueue;
import org.redisson.api.RSet;
import org.redisson.api.RStream;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamGroup;
import org.redisson.api.StreamMessageId;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.Codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RedissonAccess {

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    // === Bucket ===

    public <T> RBucket<T> getBucket(String key) {
        return redissonClient.getBucket(key);
    }

    public <T> void setBucket(String key, T value) {
        getBucket(key).set(value);
    }

    public <T> void setBucket(String key, T value, long ttlSeconds) {
        getBucket(key).set(value, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Additional type-safe TTL entry point. Zero, negative and sub-millisecond
     * durations are rejected before any Redis command is sent.
     * Existing long-based methods retain their historical behavior.
     */
    public <T> void setBucket(String key, T value, Duration ttl) {
        getBucket(key).set(value, RedisTimeArguments.positiveMillis(ttl, "ttl"), TimeUnit.MILLISECONDS);
    }

    public <T> T getBucketValue(String key, Class<T> clazz) {
        return clazz.cast(getBucket(key).get());
    }

    public boolean bucketExists(String key) {
        return getBucket(key).isExists();
    }

    public void deleteBucket(String key) {
        getBucket(key).delete();
    }

    public void expireBucket(String key, long ttlSeconds) {
        getBucket(key).expire(ttlSeconds, TimeUnit.SECONDS);
    }

    /** TTL is expressed explicitly as a Duration (legacy long overload uses seconds). */
    public boolean expireBucket(String key, Duration ttl) {
        return getBucket(key).expire(Duration.ofMillis(RedisTimeArguments.positiveMillis(ttl, "ttl")));
    }

    // === Map ===

    public <K, V> RMap<K, V> getMap(String name) {
        return redissonClient.getMap(name);
    }

    public <K, V> void putToMap(String map, K key, V value) {
        getMap(map).put(key, value);
    }

    /**
     * 寫入 Hash (Map) 結構的某個欄位值，並設定整體過期時間
     */
    public <K, T> void putMapValue(String redisKey, K key, T value, long expireMillis) {
        RMap<K, T> map = redissonClient.getMap(redisKey);
        map.put(key, value);
        map.expire(Duration.ofMillis(expireMillis));
    }

    /** Set entire Redis Hash TTL; never an expiry on the individual field. */
    public <K, T> void putMapValue(String redisKey, K key, T value, Duration ttl) {
        long millis = RedisTimeArguments.positiveMillis(ttl, "ttl");
        RMap<K, T> map = redissonClient.getMap(redisKey);
        map.put(key, value);
        map.expire(Duration.ofMillis(millis));
    }

    public <K, V> V getFromMap(String redisKey, K key, Class<V> valueType) {
        Object raw = redissonClient.getMap(redisKey).get(key);
        return objectMapper.convertValue(raw, valueType);
    }

    public <K> void removeFromMap(String redisKey, K key) {
        getMap(redisKey).remove(key);
    }

    public <K> void deletemMap(String redisKey) {
        getMap(redisKey).delete();
    }

    public <K, V> Map<K, V> getAllFromMap(String redisKey, Class<K> keyClass, Class<V> valueClass) {
        Map<Object, Object> raw = redissonClient.getMap(redisKey).readAllMap();
        return raw.entrySet().stream().collect(Collectors.toMap(
                e -> objectMapper.convertValue(e.getKey(), keyClass),
                e -> objectMapper.convertValue(e.getValue(), valueClass)));
    }

    // === Set ===

    public <T> RSet<T> getSet(String name) {
        return redissonClient.getSet(name);
    }

    public <T> void addToSet(String name, T value) {
        getSet(name).add(value);
    }

    public <T> void removeFromSet(String name, T value) {
        getSet(name).remove(value);
    }

    public <T> Set<T> getAllFromSet(String name, Class<T> clazz) {
        Set<Object> raw = redissonClient.getSet(name).readAll();
        return raw.stream().map(obj -> objectMapper.convertValue(obj, clazz)).collect(Collectors.toSet());
    }

    // === List ===

    public <T> RList<T> getList(String name) {
        return redissonClient.getList(name);
    }

    public <T> void pushToList(String name, T value) {
        getList(name).add(value);
    }

    public <T> T popFromList(String name, Class<T> clazz) {
        Object raw = redissonClient.getList(name).remove(0);
        return objectMapper.convertValue(raw, clazz);
    }

    public <T> List<T> getAllFromList(String name, Class<T> clazz) {
        List<Object> raw = redissonClient.getList(name).readAll();
        return raw.stream().map(obj -> objectMapper.convertValue(obj, clazz)).toList();
    }

    /**
     * Atomic, empty-safe Redis list pop via RDeque's LPOP. Unlike remove(0),
     * the result is null for an empty list and concurrent readers are safe.
     */
    public <T> T pollFromList(String name, Class<T> clazz) {
        java.util.Objects.requireNonNull(clazz, "clazz");
        Object raw = redissonClient.getDeque(name).pollFirst();
        return raw == null ? null : objectMapper.convertValue(raw, clazz);
    }

    // === Queue ===

    public <T> RQueue<T> getQueue(String name) {
        return redissonClient.getQueue(name);
    }

    public <T> void enqueue(String name, T value) {
        getQueue(name).add(value);
    }

    public <T> T dequeue(String name, Class<T> clazz) {
        Object raw = redissonClient.getQueue(name).poll();
        return objectMapper.convertValue(raw, clazz);
    }

    public <T> List<T> dequeueAll(String name, Class<T> clazz) {
        List<Object> raw = redissonClient.getQueue(name).readAll();
        return raw.stream().map(obj -> objectMapper.convertValue(obj, clazz)).toList();
    }

    // === AtomicLong ===

    public RAtomicLong getAtomicLong(String name) {
        return redissonClient.getAtomicLong(name);
    }

    public long increment(String name) {
        return getAtomicLong(name).incrementAndGet();
    }

    public long getAtomicValue(String name) {
        return getAtomicLong(name).get();
    }

    // === Lock ===

    public RLock getLock(String name) {
        return redissonClient.getLock(name);
    }

    public void lock(String name, long leaseSeconds) {
        RLock lock = getLock(name);
        lock.lock(leaseSeconds, TimeUnit.SECONDS);
    }

    public boolean tryLock(String name, long waitSeconds, long leaseSeconds) throws InterruptedException {
        RLock lock = getLock(name);
        return lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
    }

    public void unlock(String name) {
        RLock lock = getLock(name);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Millisecond-resolution lease; no watchdog extension after lease expires.
     * Must be unlocked on the acquiring thread. Not a cross-thread unlock API.
     */
    public void lock(String name, Duration lease) {
        getLock(name).lock(RedisTimeArguments.positiveMillis(lease, "lease"), TimeUnit.MILLISECONDS);
    }

    /** Wait can be zero; lease must be positive. Interruption is propagated. */
    public boolean tryLock(String name, Duration wait, Duration lease) throws InterruptedException {
        long waitMillis = RedisTimeArguments.nonNegativeMillis(wait, "wait");
        long leaseMillis = RedisTimeArguments.positiveMillis(lease, "lease");
        return getLock(name).tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
    }

    /** Release only a lock held by this thread and signal whether it was released. */
    public boolean unlockIfHeld(String name) {
        RLock lock = getLock(name);
        if (!lock.isHeldByCurrentThread()) return false;
        lock.unlock();
        return true;
    }

    // === BloomFilter ===

    public <T> RBloomFilter<T> getBloomFilter(String name) {
        return redissonClient.getBloomFilter(name);
    }

    public <T> void initBloomFilter(String name, long expectedInsertions, double falseProb) {
        RBloomFilter<T> filter = getBloomFilter(name);
        filter.tryInit(expectedInsertions, falseProb);
    }

    public <T> boolean bloomContains(String name, T value) {
        return getBloomFilter(name).contains(value);
    }

    public <T> void bloomAdd(String name, T value) {
        getBloomFilter(name).add(value);
    }

    // === Pub/Sub ===

    public RTopic getTopic(String name) {
        return redissonClient.getTopic(name);
    }

    public RTopic getTopic(String name, Codec codec) {
        return redissonClient.getTopic(name, codec);
    }

    public void publish(String topic, Object msg) {
        getTopic(topic).publish(msg);
    }

    public <T> int subscribe(String topic, MessageListener<T> listener) {
        return getTopic(topic).addListener(Object.class, listener);
    }

    /** Subscribe with a concrete payload type rather than legacy Object.class. */
    public <T> int subscribe(String topic, Class<T> messageType, MessageListener<T> listener) {
        java.util.Objects.requireNonNull(messageType, "messageType");
        java.util.Objects.requireNonNull(listener, "listener");
        return getTopic(topic).addListener(messageType, listener);
    }

    /** Remove only the listener id returned by subscribe; does not remove all topic listeners. */
    public void unsubscribe(String topic, int listenerId) {
        getTopic(topic).removeListener(listenerId);
    }

    public <T> void setListWithTTL(String key, List<T> list, long ttlSeconds) {
        redissonClient.getBucket(key).set(list, ttlSeconds, TimeUnit.SECONDS);
    }

    public <K, V> void setMapWithTTL(String key, Map<K, V> map, long ttlSeconds) {
        redissonClient.getBucket(key).set(map, ttlSeconds, TimeUnit.SECONDS);
    }

    public <T> void setSetWithTTL(String key, Set<T> set, long ttlSeconds) {
        redissonClient.getBucket(key).set(set, ttlSeconds, TimeUnit.SECONDS);
    }

    // ========================= TTL GET =========================

    public <T> List<T> getListFromBucket(String key, Class<T> itemClass) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, itemClass));
    }

    public <T> List<T> getListFromBucket(String key, TypeReference<List<T>> typeRef) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, typeRef);
    }

    public <K, V> Map<K, V> getMapFromBucket(String key, Class<K> keyClass, Class<V> valueClass) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, objectMapper.getTypeFactory()
                .constructMapType(Map.class, keyClass, valueClass));
    }

    public <K, V> Map<K, V> getMapFromBucket(String key, TypeReference<Map<K, V>> typeRef) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, typeRef);
    }

    public <T> Set<T> getSetFromBucket(String key, Class<T> itemClass) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, objectMapper.getTypeFactory()
                .constructCollectionType(Set.class, itemClass));
    }

    public <T> Set<T> getSetFromBucket(String key, TypeReference<Set<T>> typeRef) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, typeRef);
    }

    // ========================= TTL UTILITY =========================

    public boolean keyExists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    public boolean deleteKey(String key) {
        return redissonClient.getBucket(key).delete();
    }

    public boolean expireKey(String key, long seconds) {
        return redissonClient.getBucket(key).expire(seconds, TimeUnit.SECONDS);
    }

    /** Set TTL with explicit unit; existing expireKey(long) still uses seconds. */
    public boolean expireKey(String key, Duration ttl) {
        return redissonClient.getBucket(key).expire(
                Duration.ofMillis(RedisTimeArguments.positiveMillis(ttl, "ttl")));
    }

    // ==== Stream ====
    public StreamMessageId addToStream(String streamKey, Map<String, String> data) {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        StreamAddArgs<String, String> args = StreamAddArgs.entries(data);
        return stream.add(args);
    }

    public void createConsumerGroup(String streamKey, String groupName) {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        List<StreamGroup> groups = stream.listGroups();
        boolean exists = groups.stream().anyMatch(g -> g.getName().equals(groupName));
        if (!exists) {
            StreamCreateGroupArgs createArgs = StreamCreateGroupArgs.name(groupName).id(StreamMessageId.NEWEST);
            stream.createGroup(createArgs);
        }
    }

    public RStream<String, String> getStream(String streamKey) {
        return redissonClient.getStream(streamKey);
    }

    public Map<StreamMessageId, Map<String, String>> readFromStream(
            String streamKey, String groupName, String consumerName) {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        return stream.readGroup(
                groupName,
                consumerName,
                StreamReadGroupArgs.neverDelivered().count(1));
    }

}
