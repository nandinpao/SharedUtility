package com.agitg.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "pg")
@Data
public class PostgresClusterConfig {

    private List<DataSourceProp> write;
    private List<DataSourceProp> read;
    private String driverClassName;
    private Map<String, String> hikari;

    @Data
    public static class DataSourceProp {
        private String name;
        private String url;
        private String username;
        private String password;
    }

    @Bean
    public DataSource routingDataSource() {
        
        Map<Object, Object> targets = new HashMap<>();

        List<Object> writeKeys = new ArrayList<>();
        for (var w : write) {
            DataSource ds = create(w);
            targets.put(w.getName(), ds);
            writeKeys.add(w.getName());
        }

        List<Object> readKeys = new ArrayList<>();
        for (var r : read) {
            DataSource ds = create(r);
            targets.put(r.getName(), ds);
            readKeys.add(r.getName());
        }

        var routing = new RoutingDataSource(writeKeys, readKeys);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(targets.get(write.get(0).getName()));

        return routing;
    }

    private DataSource create(DataSourceProp prop) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(prop.getUrl());
        config.setUsername(prop.getUsername());
        config.setPassword(prop.getPassword());
        config.setDriverClassName(driverClassName);
        config.setMinimumIdle(Integer.parseInt(hikari.get("minimum-idle")));
        config.setMaximumPoolSize(Integer.parseInt(hikari.get("maximum-pool-size")));
        config.setIdleTimeout(Long.parseLong(hikari.get("idle-timeout")));
        config.setPoolName(hikari.get("pool-name") + "-" + prop.getName());
        return new HikariDataSource(config);
    }
}

