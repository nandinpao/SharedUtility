package com.agitg.database;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
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

        String basePackagesStr = environment.getProperty("pg.mapper.base-packages");
        
        if (StringUtils.hasText(basePackagesStr)) {
            String[] packages = basePackagesStr.split(",");
            String joined = String.join(",", packages);

            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                    .genericBeanDefinition(MapperScannerConfigurer.class);
            builder.addPropertyValue("basePackage", joined);
            registry.registerBeanDefinition("pgMapperScannerConfigurer", builder.getBeanDefinition());
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}