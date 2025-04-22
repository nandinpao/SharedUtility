package com.agitg.database.aspect;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.agitg.database.RoutingDataSource;

@Aspect
@Component
public class ReadOnlyConnection {

    @Before("@annotation(com.agitg.database.annotation.ReadOnly)")
    public void setReadOnly() {
        RoutingDataSource.markReadOnly();
    }

    @After("@annotation(com.agitg.database.annotation.ReadOnly)")
    public void clear() {
        RoutingDataSource.clear();
    }
}
