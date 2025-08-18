package com.agitg.database.bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.jpa")
public class JPAProperties {

    private Boolean enabled;

    private List<String> basePackage = new ArrayList<>();

    private Map<String, String> properties = new HashMap<>();

}
