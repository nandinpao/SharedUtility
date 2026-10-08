package com.agitg.database;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates the routing topology BEFORE creating connection pools. */
public final class DataSourceTopologyValidator {
    private DataSourceTopologyValidator() { }

    public record Node(String name, String url, boolean isDefault) { }
    public record Plan(boolean singleMode, String defaultName) { }

    public static Plan validate(Node single, Node legacySingle, List<Node> writers, List<Node> readers) {
        if (writers == null || readers == null) {
            throw new IllegalArgumentException("writer/reader collections must not be null");
        }
        if (single != null && legacySingle != null) {
            throw new IllegalStateException("Specify only one of pg.single or pg.default-source");
        }
        Node configuredSingle = single != null ? single : legacySingle;
        if (configuredSingle != null) {
            if (!writers.isEmpty() || !readers.isEmpty()) {
                throw new IllegalStateException("pg.single/default-source cannot be combined with pg.write/pg.read");
            }
            validateNode(configuredSingle, "pg.single/default-source");
            return new Plan(true, configuredSingle.name());
        }
        if (writers.isEmpty()) {
            throw new IllegalStateException("pg.write requires at least one writer (read-only topology is rejected)");
        }

        if (writers.size() != 1) {
            throw new IllegalStateException("Multi-writer routing is unsupported: exactly one pg.write node "
                    + "is allowed. Use PostgreSQL HA/VIP to expose a single writer endpoint.");
        }

        Set<String> names = new HashSet<>();
        int writerDefaults = 0;
        String defaultWriter = null;
        for (Node writer : writers) {
            validateNode(writer, "pg.write");
            if (!names.add(writer.name())) {
                throw new IllegalStateException("Duplicate datasource name: " + writer.name());
            }
            if (writer.isDefault()) {
                writerDefaults++;
                defaultWriter = writer.name();
            }
        }
        for (Node reader : readers) {
            validateNode(reader, "pg.read");
            if (!names.add(reader.name())) {
                throw new IllegalStateException("Duplicate datasource name: " + reader.name());
            }
            if (reader.isDefault()) {
                throw new IllegalStateException("pg.read must never have isDefault=true: " + reader.name());
            }
        }
        if (writerDefaults != 1) {
            throw new IllegalStateException("Exactly one pg.write node must specify isDefault=true; found " + writerDefaults);
        }
        return new Plan(false, defaultWriter);
    }

    private static void validateNode(Node node, String property) {
        if (node == null || node.name() == null || node.name().isBlank()) {
            throw new IllegalStateException(property + ": datasource name is required");
        }
        if (node.url() == null || node.url().isBlank()) {
            throw new IllegalStateException(property + "[" + node.name() + "]: jdbc URL is required");
        }
        if (!node.url().startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(property + "[" + node.name() + "]: PostgreSQL jdbc URL is required");
        }
    }
}
