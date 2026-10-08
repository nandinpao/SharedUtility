package com.agitg.database;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import org.aspectj.lang.annotation.Aspect;
import com.agitg.database.aspect.RoutingConnectionAspect;

/** Register advice from the library's Boot auto-configuration imports. */
@AutoConfiguration
@Conditional(PgRoutingCondition.class)
@ConditionalOnClass(Aspect.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DatabaseRoutingAutoConfiguration {
    @Bean
    public RoutingConnectionAspect routingConnectionAspect() {
        return new RoutingConnectionAspect();
    }
}
