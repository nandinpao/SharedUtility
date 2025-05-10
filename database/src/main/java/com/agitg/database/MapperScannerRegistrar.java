package com.agitg.database;

import java.util.List;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    private static final String base_packages_config = "pg.mapper.base-packages";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        List<String> basePackages = Binder.get(environment)
                .bind(base_packages_config, List.class)
                .orElse(null);

        log.debug("BasePackages Location: {}", basePackages);

        if (basePackages == null || basePackages.isEmpty()) {
            throw new IllegalArgumentException("pg.mapper.base-packages must be configured.");
        }

        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage(String.join(",", basePackages));

        registry.registerBeanDefinition("mapperScannerConfigurer",
                org.springframework.beans.factory.support.BeanDefinitionBuilder
                        .genericBeanDefinition(MapperScannerConfigurer.class, () -> configurer)
                        .getBeanDefinition());
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}