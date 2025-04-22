package com.agitg.database.aspect;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.agitg.database.RoutingDataSource;

@Aspect
@Component
public class WriteOnlyConnectionAspect {

    @Before("@annotation(com.agitg.database.annotation.WriteOnly)")
    public void setWriteOnly() {
        RoutingDataSource.markWriteOnlyRandom();
    }

    @After("@annotation(com.agitg.database.annotation.WriteOnly)")
    public void clear() {
        RoutingDataSource.clear();
    }
}