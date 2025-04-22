package com.agitg.database.bean;

import lombok.Data;

@Data
public class HikariSetting {
    private int minimumIdle;
    private int maximumPoolSize;
    private long idleTimeout;
    private String poolName;
    private int connectionTimeout;
    private int maxLifetime;
}
