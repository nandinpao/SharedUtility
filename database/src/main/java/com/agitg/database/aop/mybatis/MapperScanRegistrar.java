package com.agitg.database.aop.mybatis;

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

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        // 讀取設定值
        List<String> basePackages = Binder.get(environment)
                .bind(BASE_PACKAGES_PROPERTY, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        // 建立 MapperScannerConfigurer BeanDefinition
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);

        if (!basePackages.isEmpty()) {
            builder.addPropertyValue("basePackage", String.join(",", basePackages));
        } else {
            throw new IllegalArgumentException(
                    "At least one of '" + BASE_PACKAGES_PROPERTY + "' must be specified.");
        }

        registry.registerBeanDefinition("pgMapperScannerConfigurer", builder.getBeanDefinition());

    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}