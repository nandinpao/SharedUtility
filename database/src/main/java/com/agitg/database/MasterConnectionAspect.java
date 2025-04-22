package com.agitg.database;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.agitg.database.annotation.Master;

@Aspect
@Component
public class MasterConnectionAspect {

    @Before("@annotation(master)")
    public void useMaster(Master master) {
        RoutingDataSource.markWrite();
        if (!master.value().isEmpty()) {
            RoutingDataSource.setPreferredWrite(master.value());
        }
    }

    @After("@annotation(master)")
    public void clearMaster(Master master) {
        RoutingDataSource.clear();
    }

}
