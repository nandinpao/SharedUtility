package com.agitg.redisson.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.agitg.redisson.stream.AutoClaimPolicyResolver;
import com.agitg.redisson.stream.StreamDeliveryProcessor;
import com.agitg.redisson.stream.StreamGroupConsumerService;
import com.agitg.redisson.stream.StreamTaskConsumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Spring context wiring without an actual Redis connection (connection tests are IT). */
class RedissonAutoConfigTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(RedissonAutoConfig.class)
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class));
    }
    @Test void enabledCreatesOneSharedProcessorAndAllConsumerBeans() {
        runner().withPropertyValues("redis.enabled=true", "redis.mode=single", "redis.timeout=3000",
                        "redis.single.address=127.0.0.1:6379", "redis.stream.retry.max-deliveries=4")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(StreamDeliveryProcessor.class).size());
                    assertEquals(1, context.getBeansOfType(StreamTaskConsumer.class).size());
                    assertEquals(1, context.getBeansOfType(StreamGroupConsumerService.class).size());
                    assertEquals(1, context.getBeansOfType(AutoClaimPolicyResolver.class).size());
                });
    }
    @Test void disabledDoesNotActivateRedisInfrastructure() {
        runner().withPropertyValues("redis.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(StreamDeliveryProcessor.class).isEmpty());
                });
    }
    @Test void invalidRetryPolicyFailsSpringContextFast() {
        runner().withPropertyValues("redis.enabled=true", "redis.mode=single", "redis.timeout=3000",
                        "redis.single.address=127.0.0.1:6379", "redis.stream.retry.max-deliveries=0")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
