package com.agitg.database;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.agitg.database.aop.mybatis.MybatisConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConsumerBeanBackoffTest {
    @Configuration(proxyBeanMethods = false)
    static class ExistingConsumerBeans {
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean SqlSessionFactory sqlSessionFactory() { return mock(SqlSessionFactory.class); }
        @Bean SqlSessionTemplate sqlSessionTemplate() { return mock(SqlSessionTemplate.class); }
        @Bean static MapperScannerConfigurer mapperScannerConfigurer() {
            var scanner = new MapperScannerConfigurer();
            scanner.setBasePackage("example.consumer.no_such_package");
            return scanner;
        }
    }

    @Test void customDataSourceAndMybatisBeansRemainAuthoritativeWhenLibraryEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(ExistingConsumerBeans.class, DatabaseClusterConfig.class, MybatisConfig.class)
                .withPropertyValues("pg.enabled=true", "pg.mybatis.enabled=true",
                        "pg.single.url=jdbc:postgresql://127.0.0.1:1/nonexistent")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(DataSource.class).size());
                    assertEquals(1, context.getBeansOfType(SqlSessionFactory.class).size());
                    assertEquals(1, context.getBeansOfType(SqlSessionTemplate.class).size());
                    assertEquals(1, context.getBeansOfType(MapperScannerConfigurer.class).size());
                    assertEquals("dataSource", context.getBeanNamesForType(DataSource.class)[0]);
                });
    }
}
