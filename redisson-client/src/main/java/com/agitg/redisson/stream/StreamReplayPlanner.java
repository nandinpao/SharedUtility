package com.agitg.redisson.stream;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Manual, side-effect-free DLQ replay planning; publishing is intentionally an operator action.
 * Never assume XADD + DLQ XACK are atomic across Redis keys/slots.
 */
public final class StreamReplayPlanner {
    private StreamReplayPlanner() { }
    public record Plan(String sourceStream, String sourceGroup, String sourceMessageId,
                       String idempotencyKey, Map<String,String> payload) {
        public Plan { payload = Map.copyOf(payload); }
    }
    public static Plan prepare(Map<String,String> dlq) {
        Objects.requireNonNull(dlq, "dlq");
        String stream = required(dlq, "sourceStream");
        String group = required(dlq, "sourceGroup");
        if (!stream.matches("[A-Za-z0-9:_./-]{1,256}") || !group.matches("[A-Za-z0-9:_.-]{1,128}")) {
            throw new IllegalArgumentException("Invalid DLQ source stream or group");
        }
        String id = required(dlq, "sourceMessageId");
        if (!id.matches("[0-9]+-[0-9]+")) {
            throw new IllegalArgumentException("Malformed sourceMessageId");
        }
        Map<String,String> payload = new LinkedHashMap<>();
        dlq.forEach((key, val) -> {
            if (key != null && key.startsWith("body.") && val != null) {
                String field = key.substring(5);
                if (field.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) payload.put(field, val);
            }
        });
        if (payload.isEmpty()) {
            throw new IllegalStateException("DLQ has no permitted payload fields; retrieve original message via retention/backups");
        }
        // Callers must carry this source identity in a durable business idempotency ledger.
        return new Plan(stream, group, id, stream + "|" + group + "|" + id, payload);
    }
    private static String required(Map<String,String> source, String key) {
        String value = source.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
}
