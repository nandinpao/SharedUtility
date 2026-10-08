package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.agitg.database.annotation.Master;
import com.agitg.database.annotation.ReadOnly;
import com.agitg.database.annotation.Slave;
import com.agitg.database.aop.mybatis.MybatisConfig;
import com.agitg.database.it.mapper.RouteRecord;
import com.agitg.database.it.mapper.RouteRecordMapper;

/**
 * Two independent PostgreSQL databases distinguish routing destinations. They
 * are NOT a physical replication topology; replica lag/failover requires a
 * separate environment test, and is outside this IT's guarantee.
 */
class PostgresRoutingIT {
    private static final PostgreSQLContainer WRITER = new PostgreSQLContainer("postgres:16-alpine");
    private static final PostgreSQLContainer READER = new PostgreSQLContainer("postgres:16-alpine");
    private AnnotationConfigApplicationContext context;

    @BeforeAll static void boot() throws Exception {
        WRITER.start();
        READER.start();
        seed(WRITER, "writer");
        seed(READER, "reader");
    }

    private static void seed(PostgreSQLContainer container, String name) throws Exception {
        try (var conn = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
             var statement = conn.createStatement()) {
            statement.execute("CREATE TABLE route_marker(marker VARCHAR(30) NOT NULL)");
            statement.execute("INSERT INTO route_marker(marker) VALUES ('" + name + "')");
            statement.execute("CREATE TABLE routing_record(id BIGINT PRIMARY KEY, name VARCHAR(50))");
        }
    }

    @AfterAll static void shutdown() {
        READER.stop();
        WRITER.stop();
    }

    @BeforeEach void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("it", Map.of(
            "pg.enabled", "true",
            "pg.mybatis.enabled", "true",
            "pg.mybatis.mapper-scan-packages", "com.agitg.database.it.mapper")));
        context.register(TestBeans.class, DatabaseRoutingAutoConfiguration.class, MybatisConfig.class);
        context.refresh();
        new JdbcTemplate(writerPhysical()).update("TRUNCATE routing_record");
    }

    @AfterEach void tearDown() { if (context != null) context.close(); }

    private static DataSource writerPhysical() {
        return new DriverManagerDataSource(WRITER.getJdbcUrl(), WRITER.getUsername(), WRITER.getPassword());
    }
    private static DataSource readerPhysical() {
        return new DriverManagerDataSource(READER.getJdbcUrl(), READER.getUsername(), READER.getPassword());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestBeans {
        @Bean DataSource dataSource() {
            var router = new RoutingDataSource(List.of("writer"), List.of("reader"), "writer");
            router.setTargetDataSources(Map.of("writer", writerPhysical(), "reader", readerPhysical()));
            router.setDefaultTargetDataSource(writerPhysical());
            return router;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean RoutingService routingService(JdbcTemplate jdbc, RouteRecordMapper mapper) {
            return new RoutingService(jdbc, mapper);
        }
        @Bean OuterService outerService(RoutingService target) { return new OuterService(target); }
    }

    static class RoutingService {
        private final JdbcTemplate jdbc;
        private final RouteRecordMapper mapper;
        RoutingService(JdbcTemplate jdbc, RouteRecordMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

        @ReadOnly @Transactional(readOnly = true)
        public String readMarker() { return jdbc.queryForObject("SELECT marker FROM route_marker", String.class); }

        @Master @Transactional
        public String writeMarker() { return jdbc.queryForObject("SELECT marker FROM route_marker", String.class); }

        @Slave("reader") @Transactional(readOnly = true)
        public String namedReader() { return jdbc.queryForObject("SELECT marker FROM route_marker", String.class); }

        @ReadOnly @Transactional(readOnly = true)
        public String readMapperMarker() { return mapper.marker(); }

        @Master @Transactional
        public void insertThenFail() {
            mapper.insert(new RouteRecord(123L, "rollback"));
            throw new IllegalArgumentException("rollback forced");
        }

        @Master @Transactional
        public void insertSuccess() { mapper.insert(new RouteRecord(456L, "commit")); }
    }

    static class OuterService {
        private final RoutingService inner;
        OuterService(RoutingService inner) { this.inner = inner; }
        @ReadOnly @Transactional(readOnly = true)
        public String illegallySwitchToWriter() { return inner.writeMarker(); }
    }

    @Test void routesReadOnlyTransactionToReader() {
        assertEquals("reader", context.getBean(RoutingService.class).readMarker());
    }
    @Test void routesWriteTransactionToWriter() {
        assertEquals("writer", context.getBean(RoutingService.class).writeMarker());
    }
    @Test void explicitNamedSlaveTargetsConfiguredReader() {
        assertEquals("reader", context.getBean(RoutingService.class).namedReader());
    }
    @Test void readerFailureDoesNotFallBackToWriterAndCanRecover() {
        AtomicBoolean broken = new AtomicBoolean(false);
        DataSource physicalReader = readerPhysical();
        DataSource faultable = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                if (broken.get()) throw new SQLException("injected reader failure");
                return physicalReader.getConnection();
            }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                if (broken.get()) throw new SQLException("injected reader failure");
                return physicalReader.getConnection(user, password);
            }
        };
        RoutingDataSource routed = new RoutingDataSource(List.of("writer"), List.of("reader"), "writer");
        routed.setDefaultTargetDataSource(writerPhysical());
        routed.setTargetDataSources(Map.of("writer", writerPhysical(), "reader", faultable));
        routed.afterPropertiesSet();
        JdbcTemplate jdbc = new JdbcTemplate(routed);
        try (var scope = RoutingDataSource.readScope(null)) {
            assertEquals("reader", jdbc.queryForObject("SELECT marker FROM route_marker", String.class));
            broken.set(true);
            assertThrows(DataAccessException.class,
                    () -> jdbc.queryForObject("SELECT marker FROM route_marker", String.class));
            broken.set(false);
            assertEquals("reader", jdbc.queryForObject("SELECT marker FROM route_marker", String.class));
        }
        assertEquals("writer", jdbc.queryForObject("SELECT marker FROM route_marker", String.class));
    }
    @Test void mapperWithSelectRunsOnReader() {
        assertEquals("reader", context.getBean(RoutingService.class).readMapperMarker());
    }
    @Test void mybatisPlusBaseMapperInsertCommitsOnWriter() {
        context.getBean(RoutingService.class).insertSuccess();
        assertEquals(1, new JdbcTemplate(writerPhysical()).queryForObject(
                "SELECT COUNT(*) FROM routing_record WHERE id=456", Integer.class));
    }
    @Test void mybatisPlusInsertRollsBackWithTransactional() {
        assertThrows(IllegalArgumentException.class, () -> context.getBean(RoutingService.class).insertThenFail());
        assertEquals(0, new JdbcTemplate(writerPhysical()).queryForObject(
                "SELECT COUNT(*) FROM routing_record WHERE id=123", Integer.class));
    }
    @Test void refusesDifferentRouteInsideAnActiveTransaction() {
        var e = assertThrows(IllegalStateException.class,
                () -> context.getBean(OuterService.class).illegallySwitchToWriter());
        assertTrue(e.getMessage().contains("active transaction"));
    }
    @Test void phase42ManagedHikariPoolClosesOnRealPostgres() throws Exception {
        var node = new com.agitg.database.bean.DataSourceProp();
        node.setName("phase42-postgres-pool");
        node.setUrl(WRITER.getJdbcUrl());
        node.setUsername(WRITER.getUsername());
        node.setPassword(WRITER.getPassword());
        node.setDriverClassName("org.postgresql.Driver");
        node.setIsDefault(true);
        var config = new DatabaseClusterConfig();
        config.setSingle(node);
        DataSource managed = config.routingDataSource();
        try {
            // This test calls the @Bean factory method directly, outside a Spring context.
            // Spring normally invokes InitializingBean.afterPropertiesSet() automatically;
            // a manually constructed AbstractRoutingDataSource must be initialized explicitly.
            ((RoutingDataSource) managed).afterPropertiesSet();
            assertEquals(42, new JdbcTemplate(managed).queryForObject("SELECT 42", Integer.class));
            var pools = (RoutingPoolDiagnostics) managed;
            assertEquals(1, pools.poolStates().size());
            assertFalse(pools.poolStates().getFirst().closed());
        } finally {
            ((AutoCloseable) managed).close();
        }
        assertTrue(((RoutingPoolDiagnostics) managed).poolStates().getFirst().closed());
    }

}
