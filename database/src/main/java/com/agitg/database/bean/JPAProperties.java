package com.agitg.database.bean;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.jpa")
public class JPAProperties {

    private Boolean enabled;

}
