package com.agitg.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.agitg.database.bean.MybatisProperties;

@Configuration
public class MybatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            MybatisProperties props) throws Exception {

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);

        if (props.getTypeAliasesPackage() != null && !props.getTypeAliasesPackage().isEmpty()) {
            factoryBean.setTypeAliasesPackage(String.join(",", props.getTypeAliasesPackage()));
        }

        if (props.getMapperLocations() != null && !props.getMapperLocations().isEmpty()) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> all = new ArrayList<>();
            for (String location : props.getMapperLocations()) {
                Resource[] res = resolver.getResources(location);
                all.addAll(Arrays.asList(res));
            }
            factoryBean.setMapperLocations(all.toArray(new Resource[0]));
        }

        return factoryBean.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer(MybatisProperties props) {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        if (!Objects.isNull(props.getBasePackages()) && !props.getBasePackages().isEmpty()) {
            configurer.setBasePackage(String.join(",", props.getBasePackages()));
        }
        return configurer;
    }
}
