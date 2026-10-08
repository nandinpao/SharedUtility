package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;

/** Opt-in ONLY. Generate once on frozen Redisson 3.50.0 + JDK 25. Never recapture after upgrades. */
class RedisGoldenFixtureCaptureTest {
    @Test
    void exportRealWireBytesFromPinnedLegacyRedisson() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("phase39.captureGolden"), "opt-in fixture generator");
        assertEquals(25, Runtime.version().feature(), "Use Java 25 for the frozen baseline");
        var origin = Redisson.class.getProtectionDomain().getCodeSource().getLocation().toString();
        assertTrue(origin.contains("redisson-3.50.0.jar"),
                "Refuse to label fixtures as Phase 3.8 if runtime jar is not Redisson 3.50.0: " + origin);
        Path path = Path.of(System.getProperty("phase39.captureGoldenPath",
                "target/phase39-capture/phase3_8-redis-golden.tsv"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        // No modifications to main source files or the already sealed classpath resource.
        assertFalse(Files.exists(path), "Do not overwrite existing capture: " + path);
        List<String> rows = new ArrayList<>();
        var jarPath = Path.of(Redisson.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        rows.add("# origin=redisson-3.50.0.jar sha256=" + RedisGoldenFixtureSupport.sha256(Files.readAllBytes(jarPath)));
        rows.add("# phase=3.8 redisson=3.50.0 java=25");
        rows.add("# variant\\tid\\tchannel\\tsha256\\tbase64");
        for (String variant : List.of(RedisGoldenFixtureSupport.DEFAULT, RedisGoldenFixtureSupport.CONFIGURED)) {
            var codec = RedisGoldenFixtureSupport.codec(variant);
            for (var sample : RedisGoldenFixtureSupport.samples()) {
                byte[] encoded = RedisGoldenFixtureSupport.encode(codec, sample);
                assertEquals(sample.value(), RedisGoldenFixtureSupport.decode(codec, sample.channel(), encoded),
                        "baseline cannot self-read: " + variant + "/" + sample.id());
                rows.add(RedisGoldenFixtureSupport.row(variant, sample, encoded));
            }
        }
        Files.write(path, rows);
        assertEquals(20, rows.stream().filter(s -> !s.startsWith("#")).count());
    }
}
