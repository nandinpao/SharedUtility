package com.agitg.database.aop.mybatis;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Scanner configuration contract; source- and binary-compatible addition. */
class MybatisScannerFactorySelectionTest {

    private static MapperScannerConfigurer scanner(String... properties) {
        MockEnvironment env = new MockEnvironment();
        for (int i = 0; i < properties.length; i += 2) {
            env.setProperty(properties[i], properties[i + 1]);
        }
        return MybatisConfig.mapperScannerConfigurer(env);
    }

    @Test void legacyDefaultFactoryNameIsUnchanged() {
        MapperScannerConfigurer scanner = scanner("pg.mybatis.mapper-scan-packages[0]", "example.mapper");
        assertEquals("sqlSessionFactory", ReflectionTestUtils.getField(scanner, "sqlSessionFactoryBeanName"));
        assertEquals("example.mapper", ReflectionTestUtils.getField(scanner, "basePackage"));
    }

    @Test void consumerCanOptIntoNamedFactoryWithoutNewStarter() {
        MapperScannerConfigurer scanner = scanner(
                "pg.mybatis.mapper-scan-packages[0]", "example.mapper",
                "pg.mybatis.sql-session-factory-bean-name", "consumerSqlSessionFactory");
        assertEquals("consumerSqlSessionFactory",
                ReflectionTestUtils.getField(scanner, "sqlSessionFactoryBeanName"));
    }

    @Test void blankPackageIsRejected() {
        assertThrows(IllegalStateException.class, () -> scanner(
                "pg.mybatis.mapper-scan-packages[0]", "  "));
    }

    @Test void blankFactoryNameIsRejected() {
        assertThrows(IllegalStateException.class, () -> scanner(
                "pg.mybatis.mapper-scan-packages[0]", "example.mapper",
                "pg.mybatis.sql-session-factory-bean-name", "   "));
    }
}
