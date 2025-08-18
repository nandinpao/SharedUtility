package com.agitg.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.mybatis.spring.annotation.MapperScannerRegistrar;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.agitg.database.bean.DataSourceProp;
import com.agitg.database.bean.MybatisProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "pg")
@EnableConfigurationProperties({ MybatisProperties.class })
@Import(MapperScannerRegistrar.class)
@Component
public class DatabaseClusterConfig {

    private DataSourceProp defaultSource;
    private List<DataSourceProp> write;
    private List<DataSourceProp> read;

    @Primary
    @Bean(name = "dataSource")
    public DataSource routingDataSource() {

        log.debug("Start DatabaseClusterConfig...... ");

        if ((write == null || write.isEmpty()) && (read == null || read.isEmpty())) {
            throw new IllegalStateException("No pg.master or pg.read configuration found.");
        }

        Map<Object, Object> targets = new HashMap<>();
        List<Object> writeKeys = new ArrayList<>();
        List<Object> readKeys = new ArrayList<>();

        for (var w : write) {
            DataSource ds = create(w);
            targets.put(w.getName(), ds);
            writeKeys.add(w.getName());
        }
        for (var r : read) {
            DataSource ds = create(r);
            targets.put(r.getName(), ds);
            readKeys.add(r.getName());
        }

        String defaultKey = write.stream().filter(DataSourceProp::getIsDefault).map(DataSourceProp::getName).findFirst()
                .orElseGet(() -> read.stream().filter(DataSourceProp::getIsDefault).map(DataSourceProp::getName)
                        .findFirst().orElse(null));

        if (defaultKey == null) {
            throw new IllegalStateException("No default DataSource specified (missing isDefault=true).");
        }

        var routing = new RoutingDataSource(writeKeys, readKeys, defaultKey);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(targets.get(defaultKey));
        return routing;
    }

    private DataSource create(DataSourceProp prop) {

        log.debug(">>> driverClassName: {} --> {}", prop.getName(), prop.getDriverClassName());

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
