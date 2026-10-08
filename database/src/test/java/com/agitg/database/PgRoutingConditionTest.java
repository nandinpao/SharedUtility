package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import static org.mockito.Mockito.*;

class PgRoutingConditionTest {
    private boolean matches(MockEnvironment env) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(env);
        return new PgRoutingCondition().matches(context, mock(AnnotatedTypeMetadata.class));
    }
    @Test void disabledNeverCreatesBeans() {
        assertFalse(matches(new MockEnvironment().withProperty("pg.enabled", "false")
                .withProperty("pg.write[0].url", "jdbc:postgresql://host/x")));
    }
    @Test void explicitlyEnabledRoutesToValidationEvenWithoutUrl() {
        assertTrue(matches(new MockEnvironment().withProperty("pg.enabled", "true")));
    }
    @Test void partialWriteConfigRoutesToValidation() {
        assertTrue(matches(new MockEnvironment().withProperty("pg.write[0].name", "writer")));
    }
    @Test void noPgSettingsMeansNoAutoConfig() {
        assertFalse(matches(new MockEnvironment()));
    }
}
