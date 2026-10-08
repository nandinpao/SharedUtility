package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DatabaseRoutingContextTest {
    @Test void aspectRegistersThroughSpringAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DatabaseRoutingAutoConfiguration.class))
                .withPropertyValues("pg.enabled=true")
                .run(ctx -> {
                    assertNotNull(ctx.getBean(com.agitg.database.aspect.RoutingConnectionAspect.class));
                    assertTrue(ctx.containsBean("routingConnectionAspect"));
                });
    }
    @Test void aspectIsDisabledWhenPgDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DatabaseRoutingAutoConfiguration.class))
                .withPropertyValues("pg.enabled=false")
                .run(ctx -> assertFalse(ctx.containsBean("routingConnectionAspect")));
    }
}
