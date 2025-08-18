package com.agitg.database.bean;

import org.apache.ibatis.logging.Log;

import lombok.Data;

@Data
public class MybatisConfigurationProperties {
    private Boolean mapUnderscoreToCamelCase;
    private Integer defaultStatementTimeout;
    private Boolean cacheEnabled;
    private Class<? extends Log> logImpl;
}
