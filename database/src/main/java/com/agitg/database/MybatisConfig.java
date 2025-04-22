package com.agitg.database;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import com.agitg.database.bean.MybatisProperties;

@Configuration
public class MybatisConfig {
    
    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            MybatisProperties props
    ) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);

        if (StringUtils.hasText(props.getTypeAliasesPackage())) {
            factoryBean.setTypeAliasesPackage(props.getTypeAliasesPackage());
        }

        if (StringUtils.hasText(props.getMapperLocations())) {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(props.getMapperLocations());
            factoryBean.setMapperLocations(resources);
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
        if (StringUtils.hasText(props.getBasePackages())) {
            configurer.setBasePackage(props.getBasePackages());
        }
        return configurer;
    }
}
