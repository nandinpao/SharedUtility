package com.agitg.database.bean;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.mapper")
public class MapperProperties {
    private List<String> basePackages;
}
