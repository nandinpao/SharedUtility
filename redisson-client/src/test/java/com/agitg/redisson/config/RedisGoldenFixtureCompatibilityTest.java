package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Strict cross-release contract against immutable bytes captured by 3.50.0. */
class RedisGoldenFixtureCompatibilityTest {
    private static final Path FIXTURE = Path.of(
            "src/test/resources/compatibility/redis/phase3_8-redis-golden.tsv");

    @Test void sealedLegacyWireBytesRemainReadableWithConfiguredCodec() throws Exception {
        boolean exists = Files.isRegularFile(FIXTURE);
        if (Boolean.getBoolean("phase39.golden.required")) {
            assertTrue(exists, "BLOCKED: golden fixture not yet sealed. Follow docs/PHASE3_9_TEST_MATRIX.md");
        }
        Assumptions.assumeTrue(exists, "golden fixture capture pending: JDK25 baseline required");
        var lines = Files.readAllLines(FIXTURE);
        var expected = new HashMap<String, RedisGoldenFixtureSupport.Sample>();
        for (var sample : RedisGoldenFixtureSupport.samples()) expected.put(sample.id(), sample);
        var received = new java.util.HashSet<String>();
        int totalRows = 0;
        var configured = RedisGoldenFixtureSupport.codec(RedisGoldenFixtureSupport.CONFIGURED);
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            assertEquals(5, fields.length, "invalid fixture row");
            totalRows++;
            String variant = fields[0];
            assertTrue(List.of(RedisGoldenFixtureSupport.DEFAULT, RedisGoldenFixtureSupport.CONFIGURED).contains(variant));
            var sample = expected.get(fields[1]);
            assertNotNull(sample, "unexpected fixture name: " + fields[1]);
            assertEquals(sample.channel(), fields[2]);
            byte[] bytes = Base64.getDecoder().decode(fields[4]);
            assertEquals(fields[3], RedisGoldenFixtureSupport.sha256(bytes), "fixture SHA-256 corruption");
            assertEquals(sample.value(), RedisGoldenFixtureSupport.decode(configured, sample.channel(), bytes),
                    "OLD -> CURRENT broken: " + fields[0] + "/" + sample.id());
            received.add(variant + "/" + sample.id());
        }
        assertEquals(expected.size() * 2, received.size(), "fixture set incomplete");
        assertEquals(expected.size() * 2, totalRows, "fixture duplicated entries");
        // Same-dependency default codec interoperability ONLY. Genuine rollback
        // across Redisson versions is tested in compatibility/redis-legacy-reader.
        var oldCodec = RedisGoldenFixtureSupport.codec(RedisGoldenFixtureSupport.DEFAULT);
        for (var sample : RedisGoldenFixtureSupport.samples()) {
            var newWire = RedisGoldenFixtureSupport.encode(configured, sample);
            assertEquals(sample.value(), RedisGoldenFixtureSupport.decode(oldCodec, sample.channel(), newWire),
                    "Configured -> default codec changed within current version: " + sample.id());
        }
    }
}
