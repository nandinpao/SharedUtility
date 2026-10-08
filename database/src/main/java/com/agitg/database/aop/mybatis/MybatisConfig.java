package com.agitg.database.aop.mybatis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    /** A single canonical scan key; do not silently scan entity packages. */
    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer(Environment env) {
        Binder binder = Binder.get(env);
        List<String> packages = binder.bind("pg.mybatis.mapper-scan-packages",
                Bindable.listOf(String.class)).orElse(List.of()).stream()
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (packages.isEmpty()) {
            throw new IllegalStateException("pg.mybatis.mapper-scan-packages is required when pg.mybatis.enabled=true");
        }
        MapperScannerConfigurer config = new MapperScannerConfigurer();
        config.setSqlSessionFactoryBeanName("sqlSessionFactory");
        config.setBasePackage(String.join(",", packages));
        return config;
    }
}
