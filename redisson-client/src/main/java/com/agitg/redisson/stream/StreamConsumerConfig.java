package com.agitg.redisson.stream;

import java.io.Serializable;
import java.util.Objects;

/** Public configuration bean. Legacy no-arg and all-arg constructors retained. */
public class StreamConsumerConfig implements Serializable {
    private static final long serialVersionUID = -4497748546L;
    private String streamKey;
    private String group;
    private String consumer;
    private boolean autoAck = true;
    private boolean autoCreateGroup;
    public StreamConsumerConfig() {}
    public StreamConsumerConfig(String streamKey, String group, String consumer, boolean autoAck, boolean autoCreateGroup) {
        this.streamKey=streamKey;this.group=group;this.consumer=consumer;
        this.autoAck=autoAck;this.autoCreateGroup=autoCreateGroup;
    }
    public String getStreamKey() { return streamKey; }
    public void setStreamKey(String key) { streamKey = key; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group=group; }
    public String getConsumer() { return consumer; }
    public void setConsumer(String consumer) { this.consumer=consumer; }
    public boolean isAutoAck() { return autoAck; }
    public void setAutoAck(boolean enabled) { autoAck=enabled; }
    public boolean isAutoCreateGroup() { return autoCreateGroup; }
    public void setAutoCreateGroup(boolean enabled) { autoCreateGroup=enabled; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StreamConsumerConfig c)) return false;
        return autoAck == c.autoAck && autoCreateGroup == c.autoCreateGroup
                && Objects.equals(streamKey, c.streamKey) && Objects.equals(group, c.group)
                && Objects.equals(consumer, c.consumer);
    }
    @Override public int hashCode() { return Objects.hash(streamKey,group,consumer,autoAck,autoCreateGroup); }
    @Override public String toString() {
        return "StreamConsumerConfig(streamKey=" + streamKey + ", group=" + group
                + ", consumer=" + consumer + ", autoAck=" + autoAck
                + ", autoCreateGroup=" + autoCreateGroup + ")";
    }
}
