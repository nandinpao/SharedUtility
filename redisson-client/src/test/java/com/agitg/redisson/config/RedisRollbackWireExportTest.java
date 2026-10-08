package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Export candidate release wire bytes for a separate JVM locked to Redisson 3.50.0. */
class RedisRollbackWireExportTest {
    @Test void exportCandidateBytesForActualOldLibraryReader() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("phase39.exportForRollback"), "opt-in old-reader integration");
        Path out = Path.of("target/phase39-capture/candidate-for-legacy-reader.tsv");
        Files.createDirectories(out.toAbsolutePath().getParent());
        var lines = new ArrayList<String>();
        lines.add("# candidate wire bytes, independent Redisson 3.50.0 reader must verify all samples");
        var current = RedisGoldenFixtureSupport.codec(RedisGoldenFixtureSupport.CONFIGURED);
        for (var sample : RedisGoldenFixtureSupport.samples()) {
            byte[] raw = RedisGoldenFixtureSupport.encode(current, sample);
            assertEquals(sample.value(), RedisGoldenFixtureSupport.decode(current, sample.channel(), raw));
            lines.add(RedisGoldenFixtureSupport.row(RedisGoldenFixtureSupport.CONFIGURED, sample, raw));
        }
        Files.write(out, lines);
        assertEquals(11, lines.size());
    }
}
