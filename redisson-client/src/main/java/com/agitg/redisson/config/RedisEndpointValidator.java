package com.agitg.redisson.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Config validation that must run before Redisson starts its network connections. */
public final class RedisEndpointValidator {
    private RedisEndpointValidator() { }

    public static void validate(String mode, int timeoutMs, int database,
                                String singleAddress, List<String> clusterNodes) {
        if (mode == null || !(mode.equals("single") || mode.equals("cluster"))) {
            throw new IllegalStateException("redis.mode must be 'single' or 'cluster'");
        }
        if (timeoutMs <= 0) {
            throw new IllegalStateException("redis.timeout must be > 0 milliseconds");
        }
        if (database < 0) {
            throw new IllegalStateException("redis.database must be >= 0");
        }
        if (mode.equals("single")) {
            validateAddress(singleAddress, "redis.single.address");
        } else {
            if (database != 0) {
                throw new IllegalStateException("redis.database must be 0 in Redis Cluster mode");
            }
            if (clusterNodes == null || clusterNodes.isEmpty()) {
                throw new IllegalStateException("redis.cluster.nodes must have at least one node");
            }
            Set<String> unique = new HashSet<>();
            for (String node : clusterNodes) {
                validateAddress(node, "redis.cluster.nodes");
                if (!unique.add(node.trim())) {
                    throw new IllegalStateException("Duplicate redis.cluster.nodes address: " + node);
                }
            }
        }
    }

    private static void validateAddress(String address, String path) {
        if (address == null || address.isBlank()) {
            throw new IllegalStateException(path + " must not be blank");
        }
        // Phase 0 contract: host:port only. Scheme/TLS credentials are validated in Phase 3.
        if (address.contains("://") || !address.matches("[^\\s/:]+:[0-9]{1,5}")) {
            throw new IllegalStateException(path + " must use host:port format (no URL scheme)");
        }
        int port = Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(path + " port must be 1..65535");
        }
    }
}
