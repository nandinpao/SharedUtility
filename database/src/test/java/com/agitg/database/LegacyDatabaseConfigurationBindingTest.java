package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.agitg.database.bean.MybatisProperties;

class LegacyDatabaseConfigurationBindingTest {

    @Test
    void v1MapperScanYamlKeyStillBindsToV2CanonicalProperty() {
        var source = new MapConfigurationPropertySource(Map.of(
                "pg.mybatis.enabled", "true",
                "pg.mybatis.mapper-scan-packages[0]", "com.example.mapper"));

        var props = new Binder(source).bind("pg.mybatis", Bindable.of(MybatisProperties.class)).orElseThrow(() -> new IllegalStateException("pg.mybatis binding failed"));
        assertTrue(props.getEnabled());
        assertEquals(java.util.List.of("com.example.mapper"), props.getMapperScanPackages());
        assertEquals(props.getMapperScanPackages(), props.getMapperscanpackages());
    }

    @Test
    void legacyDefaultSourceAliasRemainsRecognizedByCondition() {
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("pg.default-source.url", "jdbc:postgresql://localhost:5432/app");
        var context = org.mockito.Mockito.mock(org.springframework.context.annotation.ConditionContext.class);
        org.mockito.Mockito.when(context.getEnvironment()).thenReturn(environment);
        assertTrue(new PgRoutingCondition().matches(context,
                org.mockito.Mockito.mock(org.springframework.core.type.AnnotatedTypeMetadata.class)));
    }
}
