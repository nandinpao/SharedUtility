package com.agitg.database;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnablePgMapperScan
@EnableConfigurationProperties({ MapperProperties.class })
public class PgStarterAutoConfiguration {
}