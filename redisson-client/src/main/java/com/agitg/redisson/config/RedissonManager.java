package com.agitg.redisson.config;

import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.yaml.snakeyaml.Yaml;

import com.agitg.redisson.bean.RedissonProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedissonManager {

    private static volatile RedissonClient redissonClient;
    private static final ReentrantLock lock = new ReentrantLock();

    public static RedissonClient getClient(InputStream in) {
        if (redissonClient == null) {
            lock.lock();
            try {
                if (redissonClient == null) {
                    redissonClient = init(in);
                }
            } catch (Exception e) {
                throw new RuntimeException("Redisson init failed", e);
            } finally {
                lock.unlock();
            }
        }
        return redissonClient;

    }

    private static RedissonClient init(InputStream in) {

        Yaml yaml = new Yaml();
        var redis = yaml.loadAs(in, RedissonProperties.class);
        Config config = new Config();
        String prefix = "redis://";

        switch (redis.getMode()) {
            case "single" -> {
                var s = redis.getSingle();
                var c = config.useSingleServer()
                        .setAddress(prefix + s.getAddress())
                        .setTimeout(redis.getTimeout())
                        .setDatabase(redis.getDatabase());
                if (redis.getPassword() != null)
                    c.setPassword(redis.getPassword());
            }
            case "cluster" -> {
                var cl = redis.getCluster();
                var c = config.useClusterServers()
                        .setScanInterval(cl.getScanInterval())
                        .setRetryAttempts(cl.getRetryAttempts())
                        .setRetryInterval(cl.getRetryInterval())
                        .setTimeout(redis.getTimeout())
                        .setSlaveConnectionPoolSize(cl.getSlaveConnectionPoolSize())
                        .setMasterConnectionPoolSize(cl.getMasterConnectionPoolSize())
                        .setReadMode(ReadMode.valueOf(cl.getReadMode().toUpperCase()));
                cl.getNodes().forEach(n -> c.addNodeAddress(prefix + n));
                if (redis.getPassword() != null)
                    c.setPassword(redis.getPassword());
            }
        }

        return Redisson.create(config);

    }
}
