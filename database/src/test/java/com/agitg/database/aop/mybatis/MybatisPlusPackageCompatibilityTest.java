package com.agitg.database.aop.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;

/**
 * Regression guard for MyBatis-Plus 3.5.17.
 *
 * <p>3.5.17 moved Spring integration classes out of the old
 * {@code com.baomidou.mybatisplus.extension.spring} split package into
 * {@code com.baomidou.mybatisplus.spring}.</p>
 */
class MybatisPlusPackageCompatibilityTest {

    @Test
    void sqlSessionFactoryBeanUsesMybatisPlus3517SpringPackage() {
        assertEquals(
                "com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean",
                MybatisSqlSessionFactoryBean.class.getName());
    }
}
