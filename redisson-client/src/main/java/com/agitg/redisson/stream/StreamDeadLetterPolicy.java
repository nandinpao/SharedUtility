package com.agitg.redisson.stream;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict payload allowlist: by default DLQ stores metadata, never message values. */
public record StreamDeadLetterPolicy(Set<String> allowedFields, int maxFields, int maxValueCharacters) {
    public StreamDeadLetterPolicy {
        Objects.requireNonNull(allowedFields, "allowedFields");
        allowedFields = Set.copyOf(allowedFields);
        if (maxFields < 0 || maxFields > 100 || maxValueCharacters < 1 || maxValueCharacters > 65536) {
            throw new IllegalArgumentException("invalid DLQ payload bounds");
        }
        for (String field : allowedFields) {
            if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
                throw new IllegalArgumentException("invalid DLQ allowlist key");
            }
        }
        if (allowedFields.size() > maxFields) {
            throw new IllegalArgumentException("DLQ allowlist exceeds maxFields");
        }
    }
    public static StreamDeadLetterPolicy metadataOnly() {
        return new StreamDeadLetterPolicy(Set.of(), 0, 256);
    }
    public Map<String,String> sanitizedBody(Map<String,String> body) {
        if (body == null || allowedFields.isEmpty()) return Map.of();
        Map<String,String> sanitized = new LinkedHashMap<>();
        for (String name : allowedFields.stream().sorted().toList()) {
            String value = body.get(name);
            if (value != null && value.length() <= maxValueCharacters) {
                sanitized.put("body." + name, value);
            }
        }
        return Map.copyOf(sanitized);
    }
}
