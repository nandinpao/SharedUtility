package com.agitg.redisson.config;

import lombok.Data;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "redis")
public class RedissonProperties {
    private int database;
    private String password;
    private int timeout;
    private String mode;
    private Pool pool;
    private RedissonSingle single;
    private RedissonCluster cluster;

    @Data
    public static class Pool {
        private int maxIdle;
        private int minIdle;
        private int maxActive;
        private int maxWait;
        private int connTimeout;
        private int soTimeout;
        private int size;
    }

    @Data
    public static class RedissonSingle {
        private String address;
    }

    @Data
    public static class RedissonCluster {
        private int scanInterval;
        private List<String> nodes;
        private String readMode;
        private int retryAttempts;
        private int failedAttempts;
        private int slaveConnectionPoolSize;
        private int masterConnectionPoolSize;
        private int retryInterval;
    }
}
