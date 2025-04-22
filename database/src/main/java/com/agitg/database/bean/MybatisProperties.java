package com.agitg.database.bean;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.mybatis")
public class MybatisProperties {
    private String mapperLocations;
    private String typeAliasesPackage;
    private String basePackages; // 支援 base-packages 掃描 mapper
}
