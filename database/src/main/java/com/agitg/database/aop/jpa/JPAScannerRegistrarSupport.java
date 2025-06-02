package com.agitg.database.aop.jpa;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

public class JPAScannerRegistrarSupport {

    private static final String BASE_PACKAGE_KEY = "pg.jpa.basePackage";

    public static void register(BeanDefinitionRegistry registry, Environment environment) {

        String rawPackages = environment.getProperty(BASE_PACKAGE_KEY, "");

        List<String> basePackages = Binder.get(environment)
                .bind(BASE_PACKAGE_KEY, Bindable.listOf(String.class))
                .orElse(Collections.emptyList());

        if (!basePackages.isEmpty()) {
            String[] packages = Arrays.stream(rawPackages.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);

            basePackages = Arrays.asList(packages);
        }

        if (basePackages.isEmpty()) {
            throw new IllegalStateException("pg.jpa.basePackage is not configured.");
        }

        EntityScanPackages.register(registry, basePackages);

        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(DynamicJpaRegistrar.class);

        registry.registerBeanDefinition("dynamicJpaRegistrar", builder.getBeanDefinition());

    }

    @EnableJpaRepositories(basePackages = {})
    public static class DynamicJpaRegistrar {
    }

}
