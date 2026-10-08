package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import com.agitg.redisson.bean.RedissonProperties;
import com.agitg.redisson.stream.AutoClaimPolicy;
import com.agitg.redisson.stream.AutoClaimPolicyResolver;
import com.agitg.redisson.stream.StreamConsumerBootstrap;
import com.agitg.redisson.stream.StreamConsumerConfig;
import com.agitg.redisson.stream.StreamGroupConsumerService;
import com.agitg.redisson.stream.StreamGroupEnsurer;
import com.agitg.redisson.stream.StreamMessageWrapper;
import com.agitg.redisson.stream.StreamTaskConsumer;
import com.agitg.redisson.stream.StreamTaskListener;
import com.fasterxml.jackson.databind.ObjectMapper;

class LegacyRedissonApiCompatibilityTest {

    @Test
    void v1ManagerConstructorAndStaticMethodRemainPresent() throws Exception {
        assertFalse(java.lang.reflect.Modifier.isFinal(RedissonManager.class.getModifiers()));
        RedissonManager.class.getConstructor();
        RedissonManager.class.getMethod("getClient", InputStream.class, ObjectMapper.class);
    }

    @Test
    void v1AutoConfigDirectCallMethodsRemainPresent() throws Exception {
        RedissonAutoConfig.class.getConstructor(RedissonProperties.class);
        RedissonAutoConfig.class.getMethod("streamTaskConsumer", RedissonClient.class, AutoClaimPolicyResolver.class);
        RedissonAutoConfig.class.getMethod("streamTaskConsumer", RedissonClient.class, AutoClaimPolicyResolver.class,
                com.agitg.redisson.stream.StreamDeliveryProcessor.class);
        RedissonAutoConfig.class.getMethod("redissonClient");
        RedissonAutoConfig.class.getMethod("redissonClient", ObjectMapper.class);
        RedissonAutoConfig.class.getMethod("redissonAccess", RedissonClient.class, ObjectMapper.class);
    }

    @Test
    void beanFactoryJavaMethodNamesAreUniqueFromCompatibilityOverloads() {
        var beanMethods = java.util.Arrays.stream(RedissonAutoConfig.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(org.springframework.context.annotation.Bean.class))
                .toList();
        for (var beanMethod : beanMethods) {
            long sameJavaName = java.util.Arrays.stream(RedissonAutoConfig.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(beanMethod.getName()))
                    .count();
            assertEquals(1L, sameJavaName, "@Bean factory Java method must not be overloaded: " + beanMethod.getName());
        }
    }

    @Test
    void v1ConsumerConstructorsAndEntryPointsRemainPresent() throws Exception {
        StreamTaskConsumer.class.getConstructor(RedissonClient.class, AutoClaimPolicyResolver.class);
        StreamGroupConsumerService.class.getConstructor(RedissonClient.class, AutoClaimPolicyResolver.class);

        StreamConsumerConfig.class.getConstructor();
        StreamConsumerConfig.class.getConstructor(String.class, String.class, String.class, boolean.class, boolean.class);
        StreamMessageWrapper.class.getConstructor();
        StreamMessageWrapper.class.getMethod("get", String.class);

        StreamConsumerBootstrap.class.getMethod("builder");
        StreamConsumerBootstrap.class.getConstructor(StreamTaskConsumer.class, StreamGroupEnsurer.class,
                StreamConsumerConfig.class, java.time.Duration.class, java.util.function.Function.class,
                java.util.concurrent.ExecutorService.class, StreamTaskListener.class);

        assertNotNull(AutoClaimPolicy.builder().enabled(true).idle(java.time.Duration.ofSeconds(1))
                .interval(java.time.Duration.ofSeconds(1)).batch(1).maxRounds(1).build());
    }
}
