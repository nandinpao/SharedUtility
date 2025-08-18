package com.agitg.database.bean;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.mybatis")
public class MybatisProperties {

    private Boolean enabled;

    private List<String> mapperLocations;
    private List<String> typeAliasesPackage;
    private List<String> basePackages;
    private List<String> typeHandlersPackage;

    private MybatisConfigurationProperties configuration;
}
