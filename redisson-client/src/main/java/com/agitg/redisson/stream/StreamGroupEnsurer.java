package com.agitg.redisson.stream;

import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class StreamGroupEnsurer {

    private final RedissonClient redisson;

    public RStream<String, String> ensureGroup(String streamKey, String group) {
        RStream<String, String> stream = redisson.getStream(streamKey);

        try {
            stream.createGroup(StreamCreateGroupArgs.name(group).id(StreamMessageId.NEWEST));
            log.info("🟢 Redis group '{}' created for key '{}'", group, streamKey);
        } catch (org.redisson.client.RedisException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("requires the key to exist")) {
                // MKSTREAM fallback
                stream.add(StreamAddArgs.entry("init", "1"));
                stream.createGroup(StreamCreateGroupArgs.name(group).id(StreamMessageId.NEWEST));
                log.info("🟢 (after MKSTREAM) Redis group '{}' created for key '{}'", group, streamKey);
            } else if (e instanceof org.redisson.client.RedisBusyException) {
                log.info("ℹ️ Redis group '{}' already exists for key '{}'", group, streamKey);
            } else {
                log.error("❌ Unable to create group '{}': {}", group, e.toString(), e);
                throw e;
            }
        }
        return stream;
    }
}
