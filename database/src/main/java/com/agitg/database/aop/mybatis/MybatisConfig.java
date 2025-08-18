package com.agitg.database.aop.mybatis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScannerRegistrar;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.agitg.database.bean.MybatisConfigurationProperties;
import com.agitg.database.bean.MybatisProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "pg.mybatis.enabled", havingValue = "true")
@EnableConfigurationProperties({ MybatisProperties.class })
@Import(MapperScannerRegistrar.class)
public class MybatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            MybatisProperties props) throws Exception {

        log.debug("Start Mybatis .....");

        // 加入 MyBatis 配置物件
        MybatisConfigurationProperties config = props.getConfiguration();
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

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
                configuration.setLogImpl((Class<? extends Log>) config.getLogImpl());
            }
        }

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);

        if (props.getTypeAliasesPackage() != null && !props.getTypeAliasesPackage().isEmpty()) {
            factoryBean.setTypeAliasesPackage(String.join(",", props.getTypeAliasesPackage()));
        }

        if (props.getMapperLocations() != null && !props.getMapperLocations().isEmpty()) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> all = new ArrayList<>();
            for (String location : props.getMapperLocations()) {

                log.debug("Mapper Location: {}", location);
                Resource[] res = resolver.getResources(location);
                all.addAll(Arrays.asList(res));
            }
            factoryBean.setMapperLocations(all.toArray(new Resource[0]));
        }

        if (props.getTypeHandlersPackage() != null && !props.getTypeHandlersPackage().isEmpty()) {
            log.debug("type-handlers-package: {}", props.getTypeHandlersPackage());
            factoryBean.setTypeHandlersPackage(String.join(",", props.getTypeHandlersPackage()));
        }

        return factoryBean.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

}
