package com.agitg.redisson.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonAutoConfig {

    private final RedissonProperties props;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String redisPrefix = "redis://";

        switch (props.getMode()) {
            case "single" -> {
                var s = props.getSingle();
                var c = config.useSingleServer()
                        .setAddress(redisPrefix + s.getAddress())
                        .setTimeout(props.getTimeout())
                        .setDatabase(props.getDatabase());
                if (props.getPassword() != null) {
                    c.setPassword(props.getPassword());
                }
            }
            case "cluster" -> {
                var cl = props.getCluster();
                var c = config.useClusterServers()
                        .setScanInterval(cl.getScanInterval())
                        .setRetryAttempts(cl.getRetryAttempts())
                        .setRetryInterval(cl.getRetryInterval())
                        .setTimeout(props.getTimeout())
                        .setSlaveConnectionPoolSize(cl.getSlaveConnectionPoolSize())
                        .setMasterConnectionPoolSize(cl.getMasterConnectionPoolSize())
                        .setReadMode(ReadMode.valueOf(cl.getReadMode().toUpperCase()));
                cl.getNodes().forEach(node -> c.addNodeAddress(redisPrefix + node));
                if (props.getPassword() != null) {
                    c.setPassword(props.getPassword());
                }
            }
        }

        return Redisson.create(config);
    }
}
