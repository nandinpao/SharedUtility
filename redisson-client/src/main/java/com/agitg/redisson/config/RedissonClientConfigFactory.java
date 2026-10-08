package com.agitg.redisson.config;

import java.util.List;
import java.util.Objects;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SslVerificationMode;
import com.agitg.redisson.bean.RedissonProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/** One config builder for Spring-managed and legacy/manual Redisson clients. */
public final class RedissonClientConfigFactory {
    private RedissonClientConfigFactory() { }

    public static Config create(RedissonProperties props, ObjectMapper mapper) {
        Objects.requireNonNull(props, "props");
        Objects.requireNonNull(mapper, "mapper");
        RedisEndpointValidator.validate(props.getMode(), props.getTimeout(), props.getDatabase(),
                props.getSingle() == null ? null : props.getSingle().getAddress(),
                props.getCluster() == null ? null : props.getCluster().getNodes());
        var tls = props.getTls();
        RedisTransportPolicy transport = new RedisTransportPolicy(
                tls != null && tls.isEnabled(), tls != null && tls.isRequired());
        // Never enable polymorphic default typing; isolate the codec from application mappers.
        ObjectMapper safeMapper = mapper.copy();
        safeMapper.deactivateDefaultTyping();
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec(safeMapper));
        if ("single".equals(props.getMode())) {
            var c = config.useSingleServer()
                    .setAddress(transport.endpoint(props.getSingle().getAddress()))
                    .setTimeout(props.getTimeout()).setDatabase(props.getDatabase());
            if (transport.enabled()) c.setSslVerificationMode(SslVerificationMode.STRICT);
            if (hasText(props.getPassword())) c.setPassword(props.getPassword());
        } else {
            var cluster = props.getCluster();
            if (cluster.getScanInterval() <= 0 || cluster.getRetryAttempts() < 0
                    || cluster.getRetryInterval() < 0 || cluster.getSlaveConnectionPoolSize() <= 0
                    || cluster.getMasterConnectionPoolSize() <= 0) {
                throw new IllegalStateException("Invalid redis.cluster scheduling or connection pool settings");
            }
            if (!hasText(cluster.getReadMode())) {
                throw new IllegalStateException("redis.cluster.read-mode is required");
            }
            final ReadMode readMode;
            try {
                readMode = ReadMode.valueOf(cluster.getReadMode().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid redis.cluster.read-mode", e);
            }
            var c = config.useClusterServers()
                    .setScanInterval(cluster.getScanInterval())
                    .setRetryAttempts(cluster.getRetryAttempts())
                    .setRetryInterval(cluster.getRetryInterval())
                    .setTimeout(props.getTimeout())
                    .setSlaveConnectionPoolSize(cluster.getSlaveConnectionPoolSize())
                    .setMasterConnectionPoolSize(cluster.getMasterConnectionPoolSize())
                    .setReadMode(readMode);
            if (transport.enabled()) c.setSslVerificationMode(SslVerificationMode.STRICT);
            List<String> nodes = cluster.getNodes();
            nodes.forEach(hostPort -> c.addNodeAddress(transport.endpoint(hostPort)));
            if (hasText(props.getPassword())) c.setPassword(props.getPassword());
        }
        return config;
    }
    private static boolean hasText(String s) { return s != null && !s.isBlank(); }
}
