package com.agitg.database;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class PgRoutingCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String enabled = env.getProperty("pg.enabled");
        if (enabled != null && "false".equalsIgnoreCase(enabled.trim())) {
            return false;
        }
        if ("true".equalsIgnoreCase(String.valueOf(enabled).trim())) {
            return true;
        }
        return hasText(env, "pg.single.name") || hasText(env, "pg.default-source.name")
                || hasText(env, "pg.write[0].name") || hasText(env, "pg.read[0].name")
                || hasText(env, "pg.single.url")
                || hasText(env, "pg.default-source.url")
                || hasText(env, "pg.defaultSource.url")
                || hasText(env, "pg.write[0].url")
                || hasText(env, "pg.read[0].url");
    }

    private boolean hasText(Environment env, String key) {
        String value = env.getProperty(key);
        return value != null && !value.isBlank();
    }
}
