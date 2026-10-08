package com.agitg.database;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import com.agitg.database.annotation.Master;
import com.agitg.database.annotation.Slave;
import com.agitg.database.aop.mybatis.MybatisConfig;
import com.agitg.database.aspect.MasterConnectionAspect;
import com.agitg.database.aspect.ReadOnlyConnection;
import com.agitg.database.aspect.SlaveConnectionAspect;
import com.agitg.database.aspect.WriteOnlyConnectionAspect;
import com.agitg.database.bean.MybatisProperties;

class LegacyDatabaseApiCompatibilityTest {

    @Test
    void legacyMybatisJavaBeanAccessorsDelegateToCanonicalProperty() {
        var props = new MybatisProperties();
        var packages = List.of("com.example.mapper");

        props.setMapperscanpackages(packages);
        assertEquals(packages, props.getMapperScanPackages());

        props.setMapperScanPackages(List.of("com.example.v2"));
        assertEquals(List.of("com.example.v2"), props.getMapperscanpackages());
    }

    @Test
    void legacyAspectClassNamesAndMethodsRemainLoadableButAreNotAdvisors() throws Exception {
        assertFalse(MasterConnectionAspect.class.isAnnotationPresent(Aspect.class));
        assertFalse(SlaveConnectionAspect.class.isAnnotationPresent(Aspect.class));
        assertFalse(ReadOnlyConnection.class.isAnnotationPresent(Aspect.class));
        assertFalse(WriteOnlyConnectionAspect.class.isAnnotationPresent(Aspect.class));

        MasterConnectionAspect.class.getConstructor();
        MasterConnectionAspect.class.getMethod("useMaster", Master.class);
        MasterConnectionAspect.class.getMethod("clearMaster", Master.class);
        SlaveConnectionAspect.class.getMethod("useSlave", Slave.class);
        SlaveConnectionAspect.class.getMethod("clearSlave", Slave.class);
        ReadOnlyConnection.class.getMethod("setReadOnly");
        ReadOnlyConnection.class.getMethod("clear");
        WriteOnlyConnectionAspect.class.getMethod("setWriteOnly");
        WriteOnlyConnectionAspect.class.getMethod("clear");
    }

    @Test
    void legacyRoutingStaticMethodsRemainPresent() throws Exception {
        RoutingDataSource.class.getMethod("markReadOnly");
        RoutingDataSource.class.getMethod("markWrite");
        RoutingDataSource.class.getMethod("markWriteOnlyRandom");
        RoutingDataSource.class.getMethod("setPreferredWrite", String.class);
        RoutingDataSource.class.getMethod("setPreferredRead", String.class);
        RoutingDataSource.class.getMethod("clear");
    }

    @Test
    void legacyMybatisProbeMethodRemainsPresentWithoutStartupSideEffects() throws Exception {
        MybatisConfig.class.getMethod("probe");
        new MybatisConfig().probe();
    }
}
