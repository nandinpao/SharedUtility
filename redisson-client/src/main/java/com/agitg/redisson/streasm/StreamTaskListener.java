package com.agitg.redisson.streasm;

import org.redisson.api.StreamMessageId;

public interface StreamTaskListener<T> {
    void onMessage(T message, StreamMessageId id);
}
