package com.agitg.redisson.streasm;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTaskConsumer implements DisposableBean {

    private final RedissonClient redisson;
    private volatile boolean running = true;

    public <T> void startConsuming(
            StreamConsumerConfig config,
            Duration timeout,
            Function<Map<String, String>, T> parser,
            ExecutorService executorService,
            StreamTaskListener<T> listener) {

        String key = config.getStreamKey();
        String group = config.getGroup();
        RStream<String, String> stream = redisson.getStream(key);

        executorService.submit(() -> {
            String consumer = config.getConsumer();
            log.info("🚀 Consumer '{}' started for stream '{}'", consumer, key);

            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        StreamReadGroupArgs args = StreamReadGroupArgs
                                .neverDelivered()
                                // .greaterThan(StreamMessageId.MIN)
                                .count(10)
                                .timeout(Duration.ofMillis(500));

                        Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(group, consumer, args);

                        if (messages == null || messages.isEmpty()) {
                            continue;
                        }

                        log.info("Receive Message: stream={}, group={}, consumer={}, count={}", key, group, consumer,
                                messages.size());

                        log.info(">>> messages: {}", messages);

                        for (Map.Entry<StreamMessageId, Map<String, String>> msg : messages.entrySet()) {
                            T task = parser.apply(msg.getValue());

                            System.out.println("Received message: " + msg.getValue());

                            listener.onMessage(task, msg.getKey());
                            stream.ack(group, msg.getKey());
                            stream.remove(msg.getKey());
                        }
                    } catch (Exception e) {
                        if (!running || Thread.currentThread().isInterrupted()) {
                            log.info("Stream consumer interrupted or stopped.");
                            break;
                        }
                        log.error("Error in stream loop", e);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

            } finally {
                log.info("🟢 Stream consumer for '{}' stopped.", key);
            }
        });
    }

    @Override
    public void destroy() {
        log.info("Destroy called - stopping StreamTaskExecutor");

        running = false;
        try {
            redisson.shutdown();
            log.info("Redisson client shutdown called");
        } catch (Exception e) {
            log.error("Error shutting down Redisson", e);
        }

        log.info("StreamTaskExecutor destroyed");
    }

}
