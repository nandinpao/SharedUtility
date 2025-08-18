package com.agitg.redisson.streasm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamConsumerConfig implements java.io.Serializable {
    
    private static final long serialVersionUID = -4497748546L;

    private String streamKey;
    private String group;
    private String consumer;
    private boolean autoAck = true;
    private boolean autoCreateGroup;
}
