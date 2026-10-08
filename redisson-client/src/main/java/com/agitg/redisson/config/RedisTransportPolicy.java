package com.agitg.redisson.config;

/** TLS selection. No password, token, or credentials may appear in a Redis endpoint. */
public record RedisTransportPolicy(boolean enabled, boolean required) {
    public RedisTransportPolicy {
        if (required && !enabled) {
            throw new IllegalStateException("redis.tls.required=true requires redis.tls.enabled=true");
        }
    }

    public String endpoint(String hostPort) {
        if (hostPort == null || hostPort.isBlank() || hostPort.contains("://")
                || hostPort.contains("@") || hostPort.contains("/") || hostPort.contains("?")) {
            throw new IllegalStateException("Redis address must be host:port without credentials or URL syntax");
        }
        return (enabled ? "rediss://" : "redis://") + hostPort;
    }
}
