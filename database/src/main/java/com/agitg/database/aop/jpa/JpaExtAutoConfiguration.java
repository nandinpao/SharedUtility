package com.agitg.database.aop.jpa;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agitg.database.bean.JPAProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 只有在 jpa.enabled=true 且 classpath 上有 JPA 時才生效。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(JPAProperties.class)
@ConditionalOnProperty(name = "pg.jpa.enabled", havingValue = "true")
public class JpaExtAutoConfiguration {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            JPAProperties extProps) {

        log.debug("Start JPA configuration: {}", extProps);

        return props -> props.putAll(extProps.flattenedProperties());
    }

}
