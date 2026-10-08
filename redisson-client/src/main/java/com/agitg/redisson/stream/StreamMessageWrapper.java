package com.agitg.redisson.stream;

import java.util.Map;
import com.agitg.sharedutility.stream.RedisStreamId;
import java.util.Objects;
import org.redisson.api.stream.StreamMessageId;

/** Context-preserving wrapper for multi-stream callbacks. */
public class StreamMessageWrapper {
    private String streamKey;
    private StreamMessageId messageId;
    private Map<String, String> body;
    public StreamMessageWrapper() {}
    public StreamMessageWrapper(String streamKey, StreamMessageId id, Map<String, String> body) {
        this.streamKey=streamKey;this.messageId=id;this.body=body;
    }
    public String getStreamKey() { return streamKey; }
    public void setStreamKey(String key) { streamKey=key; }
    public StreamMessageId getMessageId() { return messageId; }
    public void setMessageId(StreamMessageId id) { messageId=id; }
    /** Vendor-neutral Redis entry ID for callers migrating from Redisson 3.x. */
    public RedisStreamId getPortableMessageId() {
        return messageId == null ? null : RedisStreamId.parse(messageId.toString());
    }
    /** Accept a stable Stream ID without exposing Redisson package names. */
    public void setPortableMessageId(RedisStreamId id) {
        messageId = id == null ? null : new StreamMessageId(
                id.millisecondsSignedLongExact(), id.sequenceSignedLongExact());
    }
    public Map<String, String> getBody() { return body; }
    public void setBody(Map<String, String> body) { this.body=body; }
    public String get(String key) { return body==null ? null : body.get(key); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StreamMessageWrapper w)) return false;
        return Objects.equals(streamKey,w.streamKey) && Objects.equals(messageId,w.messageId)
                && Objects.equals(body,w.body);
    }
    @Override public int hashCode() { return Objects.hash(streamKey,messageId,body); }
    @Override public String toString() {
        return "StreamMessageWrapper(streamKey=" + streamKey + ", messageId=" + messageId
                + ", body=<redacted>)";
    }
}
