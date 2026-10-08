package com.agitg.redisson.config;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.yaml.snakeyaml.Yaml;
import com.agitg.redisson.bean.RedissonProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/** @deprecated Use Spring-managed RedissonClient. Static client lifecycle cannot be scope-isolated. */
@Deprecated(forRemoval = false)
public class RedissonManager {
    private static volatile RedissonClient client;
    private static final ReentrantLock LOCK = new ReentrantLock();
    /** @deprecated Static utility; constructor retained for 1.x binary/source compatibility. */
    @Deprecated(since = "2.0", forRemoval = false)
    public RedissonManager() { }
    public static RedissonClient getClient(InputStream in, ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        if (client == null) {
            LOCK.lock();
            try {
                if (client == null) {
                    Objects.requireNonNull(in, "redis config stream");
                    RedissonProperties props = new Yaml().loadAs(in, RedissonProperties.class);
                    // Sharing the config builder eliminates Spring/manual TLS and Codec drift.
                    client = Redisson.create(RedissonClientConfigFactory.create(props, mapper));
                }
            } finally {
                LOCK.unlock();
            }
        }
        return client;
    }
}
