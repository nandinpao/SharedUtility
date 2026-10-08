package com.agitg.database.aop.mybatis;

import java.util.Collections;
import java.util.List;
import com.agitg.sharedutility.database.mybatis.MapperPackagePolicy;

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

    private static final String BASE_PACKAGES_PROPERTY = "pg.mybatis.mapper-scan-packages";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        // Legacy @EnablePgMapperScan must not register a duplicate scanner
        // when MybatisConfig is already active.
        if ("true".equalsIgnoreCase(environment.getProperty("pg.mybatis.enabled"))) {
            return;
        }
        List<String> basePackages = Binder.get(environment)
                .bind(BASE_PACKAGES_PROPERTY, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        // 建立 MapperScannerConfigurer BeanDefinition
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);

        builder.addPropertyValue("basePackage",
                MapperPackagePolicy.requireBasePackage(basePackages, BASE_PACKAGES_PROPERTY));

        registry.registerBeanDefinition("pgMapperScannerConfigurer", builder.getBeanDefinition());

    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}