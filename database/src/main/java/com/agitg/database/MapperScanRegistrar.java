package com.agitg.database;

import java.util.Collections;
import java.util.List;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

public class MapperScanRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
            BeanDefinitionRegistry registry) {

        List<String> basePackages = Binder.get(environment)
                .bind("pg.mapper.base-packages", Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        List<String> mapperLocations = Binder.get(environment)
                .bind("pg.mapper.mapper-locations", Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        List<String> typeAliasesPackages = Binder.get(environment)
                .bind("pg.mapper.type-aliases-package", Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        if (basePackages.isEmpty() && mapperLocations.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one of 'pg.mapper.base-packages' or 'pg.mapper.mapper-locations' must be specified.");
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(MapperScannerConfigurer.class);

        if (!basePackages.isEmpty()) {
            builder.addPropertyValue("basePackage", String.join(",", basePackages));
        }

        if (!mapperLocations.isEmpty()) {
            builder.addPropertyValue("mapperLocations", mapperLocations.toArray(new String[0]));
        }

        if (!typeAliasesPackages.isEmpty()) {
            builder.addPropertyValue("typeAliasesPackage", String.join(",", typeAliasesPackages));
        }

        registry.registerBeanDefinition("pgMapperScannerConfigurer", builder.getBeanDefinition());
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}