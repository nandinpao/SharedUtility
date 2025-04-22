package com.agitg.database;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.agitg.database.annotation.EnablePgMapperScan;
import com.agitg.database.bean.MapperProperties;
import com.agitg.database.bean.MybatisProperties;

@Configuration
@EnablePgMapperScan
@EnableConfigurationProperties({ MapperProperties.class, MybatisProperties.class })
public class PgStarterAutoConfiguration {
}