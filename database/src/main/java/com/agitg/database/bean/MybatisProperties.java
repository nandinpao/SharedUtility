package com.agitg.database.bean;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.mapper")
public class MybatisProperties {
    private List<String> mapperLocations;
    private List<String> typeAliasesPackage;
    private List<String> basePackages; 
    private List<String> typeHandlersPackage;
}
