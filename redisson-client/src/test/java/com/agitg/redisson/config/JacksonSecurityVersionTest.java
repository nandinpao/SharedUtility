package com.agitg.redisson.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

/** Confirm the ACTUAL resolved Jackson 2 binaries, not just the POM text. */
class JacksonSecurityVersionTest {
    private static void assertPatched22(Version version) {
        assertEquals(2, version.getMajorVersion());
        assertEquals(22, version.getMinorVersion());
        assertEquals(3, version.getPatchLevel());
    }

    @Test
    void databindCoreAndJavaTimeUsePatchedJackson2() {
        assertPatched22(new ObjectMapper().version());
        assertPatched22(new JsonFactory().version());
        assertPatched22(new JavaTimeModule().version());
    }
}
