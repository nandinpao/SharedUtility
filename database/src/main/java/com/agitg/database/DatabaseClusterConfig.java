package com.agitg.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.agitg.database.bean.DataSourceProp;
import com.agitg.database.bean.MybatisProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "pg")
@EnableConfigurationProperties({ MybatisProperties.class })
@Conditional(PgRoutingCondition.class)
public class DatabaseClusterConfig {

    /**
     * Single-node datasource setting.
     *
     * Supported YAML:
     *
     * pg:
     *   single:
     *     name: single
     *     url: jdbc:postgresql://localhost:5432/app
     *     username: app
     *     password: secret
     *     isDefault: true
     *     driver-class-name: org.postgresql.Driver
     */
    private DataSourceProp single;

    /**
     * Backward-compatible alias for older config designs.
     * Spring Boot relaxed binding accepts both defaultSource and default-source.
     */
    private DataSourceProp defaultSource;

    /** Cluster write nodes. */
    private List<DataSourceProp> write;

    /** Cluster read nodes. */
    private List<DataSourceProp> read;

    @Primary
    @Bean(name = "dataSource")
    public DataSource routingDataSource() {
        log.debug("Start DatabaseClusterConfig......");

        List<DataSourceProp> writeList = safeList(write);
        List<DataSourceProp> readList = safeList(read);
        DataSourceProp singleSource = resolveSingleSource();

        // Reject invalid combinations before opening any Hikari pool.
        List<DataSourceTopologyValidator.Node> writeNodes = new ArrayList<>();
        List<DataSourceTopologyValidator.Node> readNodes = new ArrayList<>();
        for (int i = 0; i < writeList.size(); i++) {
            DataSourceProp source = normalize(writeList.get(i), "write-" + (i + 1), false);
            writeNodes.add(toNode(source));
        }
        for (int i = 0; i < readList.size(); i++) {
            DataSourceProp source = normalize(readList.get(i), "read-" + (i + 1), false);
            readNodes.add(toNode(source));
        }
        DataSourceTopologyValidator.Plan layout = DataSourceTopologyValidator.validate(
                single == null ? null : toNode(normalize(single, "single", true)),
                defaultSource == null ? null : toNode(normalize(defaultSource, "single", true)),
                writeNodes, readNodes);
        if (layout.singleMode()) {
            return createSingleRoutingDataSource(singleSource);
        }

        Map<Object, Object> targets = new HashMap<>();
        List<Object> writeKeys = new ArrayList<>();
        List<Object> readKeys = new ArrayList<>();

        for (DataSourceProp w : writeList) {
            DataSourceProp normalized = normalize(w, "write-" + (writeKeys.size() + 1), false);
            DataSource ds = create(normalized);
            targets.put(normalized.getName(), ds);
            writeKeys.add(normalized.getName());
        }

        for (DataSourceProp r : readList) {
            DataSourceProp normalized = normalize(r, "read-" + (readKeys.size() + 1), false);
            DataSource ds = create(normalized);
            targets.put(normalized.getName(), ds);
            readKeys.add(normalized.getName());
        }

        Object defaultKey = layout.defaultName();

        RoutingDataSource routing = new RoutingDataSource(writeKeys, readKeys, defaultKey);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(targets.get(defaultKey));

        log.info("PostgreSQL datasource routing initialized. mode=cluster, default={}, writes={}, reads={}",
                defaultKey, writeKeys, readKeys);

        return routing;
    }

    private DataSource createSingleRoutingDataSource(DataSourceProp source) {
        DataSourceProp normalized = normalize(source, "single", true);
        Map<Object, Object> targets = new HashMap<>();
        DataSource ds = create(normalized);
        targets.put(normalized.getName(), ds);

        List<Object> keys = Collections.singletonList(normalized.getName());
        RoutingDataSource routing = new RoutingDataSource(keys, keys, normalized.getName());
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(ds);

        log.info("PostgreSQL datasource routing initialized. mode=single, default={}", normalized.getName());

        return routing;
    }

    private DataSourceTopologyValidator.Node toNode(DataSourceProp prop) {
        return new DataSourceTopologyValidator.Node(prop.getName(), prop.getUrl(),
                Boolean.TRUE.equals(prop.getIsDefault()));
    }

    private DataSourceProp resolveSingleSource() {
        return single != null ? single : defaultSource;
    }

    private List<DataSourceProp> safeList(List<DataSourceProp> source) {
        return source == null ? Collections.emptyList() : source;
    }

    private DataSourceProp normalize(DataSourceProp prop, String fallbackName, boolean defaultWhenMissing) {
        if (prop == null) {
            throw new IllegalStateException("Invalid datasource configuration: " + fallbackName + " is null");
        }
        if (prop.getName() == null || prop.getName().isBlank()) {
            prop.setName(fallbackName);
        }
        if (prop.getDriverClassName() == null || prop.getDriverClassName().isBlank()) {
            prop.setDriverClassName("org.postgresql.Driver");
        }
        if (prop.getIsDefault() == null) {
            prop.setIsDefault(defaultWhenMissing);
        }
        return prop;
    }

    private DataSource create(DataSourceProp prop) {
        log.debug(">>> PostgreSQL DataSource: {} -> {}", prop.getName(), prop.getUrl());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(prop.getUrl());
        config.setUsername(prop.getUsername());
        config.setPassword(prop.getPassword());
        config.setDriverClassName(prop.getDriverClassName());

        if (prop.getHikari() != null) {
            config.setMinimumIdle(prop.getHikari().getMinimumIdle());
            config.setMaximumPoolSize(prop.getHikari().getMaximumPoolSize());
            config.setIdleTimeout(prop.getHikari().getIdleTimeout());
            config.setConnectionTimeout(prop.getHikari().getConnectionTimeout());
            config.setMaxLifetime(prop.getHikari().getMaxLifetime());
            config.setPoolName(prop.getHikari().getPoolName());
        }

        return new HikariDataSource(config);
    }
}
