package com.agitg.database.aop.mybatis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.agitg.database.bean.MybatisConfigurationProperties;
import com.agitg.database.bean.MybatisProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(name = "pg.mybatis.enabled", havingValue = "true")
@Configuration
@EnableConfigurationProperties({ MybatisProperties.class })
public class MybatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(
            DataSource dataSource,
            MybatisProperties props) throws Exception {

        log.info("Start Mybatis: {} ", props);

        MybatisConfigurationProperties config = props.getConfiguration();
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

        if (config != null) {
            if (config.getMapUnderscoreToCamelCase() != null) {
                configuration.setMapUnderscoreToCamelCase(config.getMapUnderscoreToCamelCase());
            }
            if (config.getDefaultStatementTimeout() != null) {
                configuration.setDefaultStatementTimeout(config.getDefaultStatementTimeout());
            }
            if (config.getCacheEnabled() != null) {
                configuration.setCacheEnabled(config.getCacheEnabled());
            }
            if (config.getLogImpl() != null) {
                configuration.setLogImpl((Class<? extends org.apache.ibatis.logging.Log>) config.getLogImpl());
            }
        }

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);

        if (props.getTypeAliasesPackage() != null && !props.getTypeAliasesPackage().isEmpty()) {
            factoryBean.setTypeAliasesPackage(String.join(",", props.getTypeAliasesPackage()));
        }

        if (props.getMapperLocations() != null && !props.getMapperLocations().isEmpty()) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> all = new ArrayList<>();
            for (String location : props.getMapperLocations()) {
                log.debug(">>>>> Mapper Location: {}", location);
                Resource[] res = resolver.getResources(location);
                all.addAll(Arrays.asList(res));
            }
            factoryBean.setMapperLocations(all.toArray(new Resource[0]));
        }

        if (props.getTypeHandlersPackage() != null && !props.getTypeHandlersPackage().isEmpty()) {
            log.debug("type-handlers-package: {}", props.getTypeHandlersPackage());
            factoryBean.setTypeHandlersPackage(String.join(",", props.getTypeHandlersPackage()));
        }

        return factoryBean.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    /**
     * 關鍵：用 Environment 讀取 YAML，組態早於一般 @Bean 初始化執行，
     * 無需 @MapperScan，也不必 @Import MapperScannerRegistrar。
     * 記得 static。
     */

    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer(Environment env) {
        Binder binder = Binder.get(env);

        List<String> pkgList = new ArrayList<>();

        // 支援多種鍵名（破折號／駝峰／點分隔）
        for (String key : Arrays.asList(
                "pg.mybatis.mapper-scan-packages",
                "pg.mybatis.mapper-scan-packages",
                "pg.mybatis.mapper.scan.packages")) {
            // ✅ 正確用法：用 Bindable 取 List<String>
            BindResult<List<String>> br = binder.bind(key, Bindable.listOf(String.class));
            if (br.isBound() && br.get() != null) {
                pkgList.addAll(br.get());
            } else {
                // 有些環境用逗號字串
                BindResult<String> brStr = binder.bind(key, Bindable.of(String.class));
                if (brStr.isBound() && brStr.get() != null) {
                    for (String s : brStr.get().split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty())
                            pkgList.add(t);
                    }
                }
            }
        }

        log.info("[MyBatis] resolved mapper scan packages = {}", pkgList);

        // 備援：退回用 type-aliases-package（至少能跑起來）
        if (pkgList.isEmpty()) {
            BindResult<List<String>> alias = binder.bind(
                    "pg.mybatis.type-aliases-package",
                    Bindable.listOf(String.class));
            if (alias.isBound() && alias.get() != null) {
                pkgList.addAll(alias.get());
                log.warn("[MyBatis] mapper-scan-packages 未設定，退回使用 type-aliases-package: {}", pkgList);
            }
        }

        if (pkgList.isEmpty()) {
            throw new IllegalStateException(
                    "missing property: 'pg.mybatis.mapper-scan-packages'. Please configure mapper interface packages.");
        }

        MapperScannerConfigurer c = new MapperScannerConfigurer();
        c.setSqlSessionFactoryBeanName("sqlSessionFactory");
        c.setBasePackage(String.join(",", pkgList));
        // 只掃描有 @Mapper 的介面（需要的話開啟）
        // c.setAnnotationClass(org.apache.ibatis.annotations.Mapper.class);

        log.info("[MyBatis] MapperScannerConfigurer basePackage = {}", String.join(",", pkgList));
        return c;
    }
}
