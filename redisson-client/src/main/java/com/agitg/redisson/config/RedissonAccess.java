package com.agitg.redisson.config;

import java.time.Duration;
import java.util.HashMap;
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
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
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

    public <K, V> V getFromMap(String redisKey, K key, Class<V> valueType) {
        Object raw = redissonClient.getMap(redisKey).get(key);
        return MAPPER.convertValue(raw, valueType);
    }

    public <K> void removeFromMap(String map, K key) {
        getMap(map).remove(key);
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

    public void publish(String topic, Object msg) {
        getTopic(topic).publish(msg);
    }

    public <T> int subscribe(String topic, MessageListener<T> listener) {
        return getTopic(topic).addListener(Object.class, listener);
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
        return objectMapper.convertValue(raw, new TypeReference<>() {
        });
    }

    public <T> List<T> getListFromBucket(String key, TypeReference<List<T>> typeRef) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, typeRef);
    }

    public <K, V> Map<K, V> getMapFromBucket(String key, Class<K> keyClass, Class<V> valueClass) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, new TypeReference<>() {
        });
    }

    public <K, V> Map<K, V> getMapFromBucket(String key, TypeReference<Map<K, V>> typeRef) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, typeRef);
    }

    public <T> Set<T> getSetFromBucket(String key, Class<T> itemClass) {
        Object raw = redissonClient.getBucket(key).get();
        return objectMapper.convertValue(raw, new TypeReference<>() {
        });
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
}
