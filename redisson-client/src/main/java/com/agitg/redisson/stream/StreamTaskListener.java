package com.agitg.redisson.stream;

import org.redisson.api.stream.StreamMessageId;

public interface StreamTaskListener<T> {
    void onMessage(T message, StreamMessageId id);
}
