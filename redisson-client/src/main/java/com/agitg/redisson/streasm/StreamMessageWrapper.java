package com.agitg.redisson.streasm;

import java.util.Map;

import org.redisson.api.StreamMessageId;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessageWrapper {

    private String streamKey;
    private StreamMessageId messageId;
    private Map<String, String> body;

    public String get(String key) {
        return body.get(key);
    }

}
