package com.agitg.database.aop.jpa;

import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.agitg.database.bean.JPAProperties;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "pg.jpa.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JPAProperties.class)
@EnableJpaRepositories(basePackages = "#{@jpaBasePackages}")
@RequiredArgsConstructor
public class JPAConfig {

    // 讓 @EnableJpaRepositories 可以取到你在 properties 裡設定的 basePackage
    @Bean
    public String[] jpaBasePackages(JPAProperties props) {
        var basePackages = props.getBasePackage();
        if (basePackages == null || basePackages.isEmpty()) {
            throw new IllegalStateException("pg.jpa.base-package is not configured.");
        }
        return basePackages.toArray(new String[0]);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            JPAProperties props) {
        var basePackages = jpaBasePackages(props);

        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(basePackages);

        var vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false); // 用 hibernate.hbm2ddl.auto 控制
        vendorAdapter.setShowSql(false); // 若要顯示 SQL，改用 properties 來控管
        factory.setJpaVendorAdapter(vendorAdapter);

        var jpaProps = new Properties();
        jpaProps.putAll(props.getProperties()); // 確保是扁平 key，如 hibernate.jdbc_batch_size
        factory.setJpaProperties(jpaProps);

        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
