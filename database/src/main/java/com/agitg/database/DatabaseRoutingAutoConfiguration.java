package com.agitg.database;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import org.aspectj.lang.annotation.Aspect;
import com.agitg.database.aspect.RoutingConnectionAspect;
import com.agitg.database.aspect.MasterConnectionAspect;
import com.agitg.database.aspect.SlaveConnectionAspect;
import com.agitg.database.aspect.ReadOnlyConnection;
import com.agitg.database.aspect.WriteOnlyConnectionAspect;

/** Register advice from the library's Boot auto-configuration imports. */
@AutoConfiguration
@Conditional(PgRoutingCondition.class)
@ConditionalOnClass(Aspect.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DatabaseRoutingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RoutingConnectionAspect.class)
    public RoutingConnectionAspect routingConnectionAspect() {
        return new RoutingConnectionAspect();
    }

    // 1.x compatibility beans. They are intentionally plain facades, not advisors.
    @Bean @ConditionalOnMissingBean(MasterConnectionAspect.class)
    public MasterConnectionAspect masterConnectionAspect() { return new MasterConnectionAspect(); }
    @Bean @ConditionalOnMissingBean(SlaveConnectionAspect.class)
    public SlaveConnectionAspect slaveConnectionAspect() { return new SlaveConnectionAspect(); }
    @Bean @ConditionalOnMissingBean(ReadOnlyConnection.class)
    public ReadOnlyConnection readOnlyConnection() { return new ReadOnlyConnection(); }
    @Bean @ConditionalOnMissingBean(WriteOnlyConnectionAspect.class)
    public WriteOnlyConnectionAspect writeOnlyConnectionAspect() { return new WriteOnlyConnectionAspect(); }
}
