package com.agitg.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.agitg.database.bean.MybatisProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableConfigurationProperties({ MybatisProperties.class })
@Import(MapperScannerRegistrar.class)
public class MybatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            MybatisProperties props) throws Exception {

        log.debug("Start Mybatis .....");

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);

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
