package com.agitg.database.aop.mybatis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.agitg.database.bean.MybatisConfigurationProperties;
import com.agitg.database.bean.MybatisProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(name = "pg.mybatis.enabled", havingValue = "true")
@Configuration
@EnableConfigurationProperties({ MybatisProperties.class })
public class MybatisConfig {

    @Bean
    @ConditionalOnMissingBean(SqlSessionFactory.class)
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            MybatisProperties props) throws Exception {

        log.info("Start Mybatis: {} ", props);

        MybatisConfigurationProperties config = props.getConfiguration();
        MybatisConfiguration configuration = new MybatisConfiguration();

        if (config != null) {
            if (config.getMapUnderscoreToCamelCase() != null) {
                configuration.setMapUnderscoreToCamelCase(config.getMapUnderscoreToCamelCase());
            }
            if (config.getDefaultStatementTimeout() != null) {
                configuration.setDefaultStatementTimeout(config.getDefaultStatementTimeout());
            }
            if (config.getCacheEnabled() != null) {
                configuration.setCacheEnabled(config.getCacheEnabled());
            }
            if (config.getLogImpl() != null) {
                configuration.setLogImpl((Class<? extends org.apache.ibatis.logging.Log>) config.getLogImpl());
            }
        }

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);

        if (props.getTypeAliasesPackage() != null && !props.getTypeAliasesPackage().isEmpty()) {
            factoryBean.setTypeAliasesPackage(String.join(",", props.getTypeAliasesPackage()));
        }

        if (props.getMapperLocations() != null && !props.getMapperLocations().isEmpty()) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> all = new ArrayList<>();
            for (String location : props.getMapperLocations()) {
                log.debug(">>>>> Mapper Location: {}", location);
                Resource[] res = resolver.getResources(location);
                all.addAll(Arrays.asList(res));
            }
            factoryBean.setMapperLocations(all.toArray(new Resource[0]));
        }

        if (props.getTypeHandlersPackage() != null && !props.getTypeHandlersPackage().isEmpty()) {
            log.debug("type-handlers-package: {}", String.join(",", props.getTypeHandlersPackage()));
            factoryBean.setTypeHandlersPackage(String.join(",", props.getTypeHandlersPackage()));
        }

        return factoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean(SqlSessionTemplate.class)
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    /** A single canonical scan key; do not silently scan entity packages. */
    @Bean
    @ConditionalOnMissingBean(MapperScannerConfigurer.class)
    public static MapperScannerConfigurer mapperScannerConfigurer(Environment env) {
        Binder binder = Binder.get(env);
        List<String> packages = binder.bind("pg.mybatis.mapper-scan-packages",
                Bindable.listOf(String.class)).orElse(List.of()).stream()
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (packages.isEmpty()) {
            throw new IllegalStateException("pg.mybatis.mapper-scan-packages is required when pg.mybatis.enabled=true");
        }
        MapperScannerConfigurer config = new MapperScannerConfigurer();
        // Default remains the historical bean name. Consumers with a custom
        // SqlSessionFactory bean may explicitly select its name without creating
        // a competing scanner or changing shared package scanning semantics.
        String factoryBeanName = binder.bind("pg.mybatis.sql-session-factory-bean-name", String.class)
                .orElse("sqlSessionFactory").trim();
        if (factoryBeanName.isEmpty()) {
            throw new IllegalStateException("pg.mybatis.sql-session-factory-bean-name must not be blank");
        }
        config.setSqlSessionFactoryBeanName(factoryBeanName);
        config.setBasePackage(String.join(",", packages));
        return config;
    }
    /**
     * @deprecated Diagnostic hook present in 1.x. It no longer performs reflective
     * classloader probing because that is unsafe on modern JDKs.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public void probe() {
        log.debug("MybatisConfig.probe() is retained as a compatibility no-op");
    }
}
