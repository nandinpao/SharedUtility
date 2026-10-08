package com.agitg.redisson.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from redis.stream.retry.*. Reject invalid settings at bean creation. */
@ConfigurationProperties(prefix = "redis.stream.retry")
public class StreamRetryProperties {
    private int maxDeliveries = 5;
    private String deadLetterSuffix = ":dlq";

    public int getMaxDeliveries() { return maxDeliveries; }
    public void setMaxDeliveries(int maxDeliveries) { this.maxDeliveries = maxDeliveries; }
    public String getDeadLetterSuffix() { return deadLetterSuffix; }
    public void setDeadLetterSuffix(String deadLetterSuffix) { this.deadLetterSuffix = deadLetterSuffix; }
    public StreamRetryPolicy toPolicy() { return new StreamRetryPolicy(maxDeliveries, deadLetterSuffix); }
}
