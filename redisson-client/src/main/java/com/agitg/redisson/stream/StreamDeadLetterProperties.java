package com.agitg.redisson.stream;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** redis.stream.dead-letter.*: field values excluded unless explicitly listed. */
@ConfigurationProperties(prefix = "redis.stream.dead-letter")
public class StreamDeadLetterProperties {
    private List<String> allowedFields = List.of();
    private int maxFields = 16;
    private int maxValueCharacters = 256;
    public List<String> getAllowedFields() { return allowedFields; }
    public void setAllowedFields(List<String> fields) { allowedFields = fields; }
    public int getMaxFields() { return maxFields; }
    public void setMaxFields(int v) { maxFields = v; }
    public int getMaxValueCharacters() { return maxValueCharacters; }
    public void setMaxValueCharacters(int v) { maxValueCharacters = v; }
    public StreamDeadLetterPolicy toPolicy() {
        if (allowedFields == null || Set.copyOf(allowedFields).size() != allowedFields.size()) {
            throw new IllegalStateException("DLQ allowlist is null or contains duplicates");
        }
        return new StreamDeadLetterPolicy(Set.copyOf(allowedFields), maxFields, maxValueCharacters);
    }
}
