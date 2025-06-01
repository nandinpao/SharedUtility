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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MapperScanRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    private static final String BASE_PACKAGES_PROPERTY = "pg.mapper.base-packages";
    private static final String MAPPER_LOCATIONS_PROPERTY = "pg.mapper.mapper-locations";
    private static final String TYPE_ALIASES_PACKAGE_PROPERTY = "pg.mapper.type-aliases-package";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        // 讀取設定值
        List<String> basePackages = Binder.get(environment)
                .bind(BASE_PACKAGES_PROPERTY, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        List<String> mapperLocations = Binder.get(environment)
                .bind(MAPPER_LOCATIONS_PROPERTY, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        List<String> typeAliasesPackages = Binder.get(environment)
                .bind(TYPE_ALIASES_PACKAGE_PROPERTY, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        // 驗證設定
        if (basePackages.isEmpty() && mapperLocations.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one of '" + BASE_PACKAGES_PROPERTY + "' or '" + MAPPER_LOCATIONS_PROPERTY
                            + "' must be specified.");
        }

        // 建立 MapperScannerConfigurer BeanDefinition
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);

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