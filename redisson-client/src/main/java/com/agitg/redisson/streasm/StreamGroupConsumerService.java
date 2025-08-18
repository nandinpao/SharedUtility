package com.agitg.redisson.streasm;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamMultiReadGroupArgs;
import org.redisson.client.RedisBusyException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamGroupConsumerService {

    private final RedissonClient redisson;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void startConsumer(StreamConsumerConfig config, Duration timeout, Consumer<StreamMessageWrapper> handler) {
        executor.submit(() -> {
            String streamKey = config.getStreamKey();
            String group = config.getGroup();
            String consumer = config.getConsumer();

            RStream<String, String> stream = redisson.getStream(streamKey);

            // 建立 group（若不存在）
            try {
                stream.createGroup(StreamCreateGroupArgs.name(group).id(StreamMessageId.NEWEST));
                log.info("🧪 Group '{}' created on stream '{}'", group, streamKey);
            } catch (RedisBusyException ignored) {
                log.debug("Group '{}' already exists on '{}'", group, streamKey);
            }

            while (true) {
                try {

                    StreamMultiReadGroupArgs args = StreamMultiReadGroupArgs
                            .greaterThan(StreamMessageId.MIN, Map.of(streamKey, StreamMessageId.NEWEST))
                            .count(1)
                            .timeout(Duration.ofSeconds(5));

                    Map<String, Map<StreamMessageId, Map<Object, Object>>> raw = redisson.getStream(streamKey)
                            .readGroup(group, consumer, args);

                    Map<String, Map<StreamMessageId, Map<String, String>>> result = castToTypedResult(raw);
                    if (result.isEmpty())
                        continue;

                    for (var entry : result.entrySet()) {
                        for (var msg : entry.getValue().entrySet()) {
                            StreamMessageWrapper wrapper = new StreamMessageWrapper(
                                    entry.getKey(), msg.getKey(), msg.getValue());

                            try {
                                handler.accept(wrapper);
                                if (config.isAutoAck()) {
                                    stream.ack(group, msg.getKey());
                                    stream.remove(msg.getKey()); // optional: clean up
                                }
                            } catch (Exception ex) {
                                log.error("❌ Error handling stream msg", ex);
                                // 可放入 retry/pending queue
                            }
                        }
                    }

                } catch (Exception e) {
                    log.error("💥 Stream read error", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<StreamMessageId, Map<String, String>>> castToTypedResult(
            Map<String, Map<StreamMessageId, Map<Object, Object>>> raw) {

        Map<String, Map<StreamMessageId, Map<String, String>>> result = new HashMap<>();
        for (var streamEntry : raw.entrySet()) {
            Map<StreamMessageId, Map<String, String>> inner = new LinkedHashMap<>();
            for (var msgEntry : streamEntry.getValue().entrySet()) {
                Map<String, String> body = new HashMap<>();
                msgEntry.getValue().forEach((k, v) -> body.put(k.toString(), v.toString()));
                inner.put(msgEntry.getKey(), body);
            }
            result.put(streamEntry.getKey(), inner);
        }
        return result;
    }
}
