package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceTopologyValidatorTest {
    private static DataSourceTopologyValidator.Node db(String name, boolean defaultNode) {
        return new DataSourceTopologyValidator.Node(name, "jdbc:postgresql://localhost/test", defaultNode);
    }
    private static DataSourceTopologyValidator.Plan validate(
            DataSourceTopologyValidator.Node single, List<DataSourceTopologyValidator.Node> writers,
            List<DataSourceTopologyValidator.Node> readers) {
        return DataSourceTopologyValidator.validate(single, null, writers, readers);
    }
    @Test void validSingleNode() {
        var p = validate(db("standalone", true), List.of(), List.of());
        assertTrue(p.singleMode());
        assertEquals("standalone", p.defaultName());
    }
    @Test void validClusterAlwaysSelectsWriterAsDefault() {
        var p = validate(null, List.of(db("write", true)), List.of(db("read", false)));
        assertFalse(p.singleMode());
        assertEquals("write", p.defaultName());
    }
    @Test void readerCannotBeDefault() {
        var e = assertThrows(IllegalStateException.class, () ->
                validate(null, List.of(db("write", true)), List.of(db("read", true))));
        assertTrue(e.getMessage().contains("pg.read"));
    }
    @Test void missingWriterRejected() {
        assertThrows(IllegalStateException.class, () ->
                validate(null, List.of(), List.of(db("read", false))));
    }
    @Test void emptyTopologyRejected() {
        assertThrows(IllegalStateException.class, () -> validate(null, List.of(), List.of()));
    }
    @Test void multipleDefaultWritersRejected() {
        assertThrows(IllegalStateException.class, () ->
                validate(null, List.of(db("a", true), db("b", true)), List.of()));
    }
    @Test void multipleWritersRejectedEvenWithSingleDefault() {
        var exception = assertThrows(IllegalStateException.class, () ->
                validate(null, List.of(db("writer1", true), db("writer2", false)), List.of()));
        assertTrue(exception.getMessage().contains("Multi-writer"));
    }
    @Test void missingWriterDefaultRejected() {
        assertThrows(IllegalStateException.class, () ->
                validate(null, List.of(db("a", false)), List.of()));
    }
    @Test void duplicateWriterAndReaderNameRejected() {
        assertThrows(IllegalStateException.class, () ->
                validate(null, List.of(db("same", true)), List.of(db("same", false))));
    }
    @Test void duplicateWriterNamesRejected() {
        assertThrows(IllegalStateException.class, () ->
                validate(null, List.of(db("same", true), db("same", false)), List.of()));
    }
    @Test void missingUrlRejected() {
        var invalid = new DataSourceTopologyValidator.Node("writer", "", true);
        assertThrows(IllegalStateException.class, () -> validate(null, List.of(invalid), List.of()));
    }
    @Test void wrongJdbcSchemeRejected() {
        var invalid = new DataSourceTopologyValidator.Node("writer", "jdbc:mysql://localhost/test", true);
        assertThrows(IllegalStateException.class, () -> validate(null, List.of(invalid), List.of()));
    }
    @Test void mixedSingleAndClusterRejected() {
        assertThrows(IllegalStateException.class, () ->
                validate(db("single", true), List.of(db("w", true)), List.of()));
    }
    @Test void bothSingleAliasesRejected() {
        assertThrows(IllegalStateException.class, () -> DataSourceTopologyValidator.validate(
                db("a", true), db("b", true), List.of(), List.of()));
    }
    @Test void legacySingleAliasAllowed() {
        assertEquals("legacy", DataSourceTopologyValidator.validate(
                null, db("legacy", true), List.of(), List.of()).defaultName());
    }
    @Test void nullAndEmptyNodesRejected() {
        assertThrows(IllegalStateException.class, () -> validate(null, java.util.Arrays.asList((DataSourceTopologyValidator.Node)null), List.of()));
    }
}
