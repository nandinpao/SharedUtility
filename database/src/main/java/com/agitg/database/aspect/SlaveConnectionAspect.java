package com.agitg.database.aspect;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.agitg.database.RoutingDataSource;
import com.agitg.database.annotation.Slave;

@Aspect
@Component
public class SlaveConnectionAspect {

    @Before("@annotation(slave)")
    public void useSlave(Slave slave) {
        RoutingDataSource.markReadOnly();
        RoutingDataSource.setPreferredRead(slave.value());
    }

    @After("@annotation(slave)")
    public void clearSlave(Slave slave) {
        RoutingDataSource.clear();
    }
}