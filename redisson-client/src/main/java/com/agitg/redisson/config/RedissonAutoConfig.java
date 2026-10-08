package com.agitg.redisson.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agitg.redisson.bean.RedissonProperties;
import com.agitg.redisson.stream.AutoClaimPolicyResolver;
import com.agitg.redisson.stream.AutoClaimProperties;
import com.agitg.redisson.stream.StreamGroupEnsurer;
import com.agitg.redisson.stream.StreamGroupConsumerService;
import com.agitg.redisson.stream.StreamDeliveryProcessor;
import com.agitg.redisson.stream.StreamRetryProperties;
import com.agitg.redisson.stream.StreamDeadLetterProperties;
import com.agitg.redisson.stream.StreamPendingSnapshot;
import com.agitg.redisson.stream.StreamMonitoringProperties;
import com.agitg.redisson.stream.StreamMonitoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import com.agitg.redisson.stream.StreamTaskConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "redis", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@EnableConfigurationProperties({ RedissonProperties.class, AutoClaimProperties.class, StreamRetryProperties.class, StreamDeadLetterProperties.class, StreamMonitoringProperties.class })
public class RedissonAutoConfig {

    private final RedissonProperties props;

    @Bean
    @ConditionalOnMissingBean
    public AutoClaimPolicyResolver autoClaimPolicyResolver(AutoClaimProperties properties) {
        return new AutoClaimPolicyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redissonObjectMapper")
    public ObjectMapper redissonObjectMapper() {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        om.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.enable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
        om.deactivateDefaultTyping(); // 明確關閉（避免被別處開啟）
        // 可視需要：忽略未知欄位（若舊資料曾帶 @class 等欄位）
        om.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;

    }

    @Bean
    @ConditionalOnMissingBean
    public StreamGroupEnsurer streamGroupEnsurer(RedissonClient redissonClient) {
        return new StreamGroupEnsurer(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamDeliveryProcessor streamDeliveryProcessor(RedissonClient client, StreamRetryProperties properties,
            StreamDeadLetterProperties deadLetterProperties) {
        return new StreamDeliveryProcessor(client, properties.toPolicy(), deadLetterProperties.toPolicy());
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamTaskConsumer streamTaskConsumer(RedissonClient redissonClient, AutoClaimPolicyResolver resolver,
                                                  StreamDeliveryProcessor processor) {
        return new StreamTaskConsumer(redissonClient, resolver, processor);
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamGroupConsumerService streamGroupConsumerService(RedissonClient client,
            AutoClaimPolicyResolver resolver, StreamTaskConsumer taskConsumer, StreamDeliveryProcessor processor) {
        return new StreamGroupConsumerService(client, resolver, taskConsumer, processor);
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamPendingSnapshot streamPendingSnapshot(RedissonClient client, StreamRetryProperties retry) {
        return new StreamPendingSnapshot(client, retry.toPolicy().deadLetterSuffix());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "redis.stream.monitoring", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public StreamMonitoringService streamMonitoringService(StreamPendingSnapshot snapshot,
            MeterRegistry registry, StreamMonitoringProperties props) {
        return new StreamMonitoringService(snapshot, registry, props);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(@Qualifier("redissonObjectMapper") ObjectMapper mapper) {
        return Redisson.create(RedissonClientConfigFactory.create(props, mapper));
    }

    @Bean
    @ConditionalOnMissingBean
    public RedissonAccess redissonAccess(RedissonClient client,
                                         @Qualifier("redissonObjectMapper") ObjectMapper objectMapper) {
        return new RedissonAccess(client, objectMapper);
    }

}
