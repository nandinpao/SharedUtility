package com.agitg.database.bean;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.mybatis")
public class MybatisProperties {

    private Boolean enabled;

    private List<String> mapperLocations;
    private List<String> typeAliasesPackage;
    private List<String> mapperScanPackages;
    private List<String> typeHandlersPackage;

    private MybatisConfigurationProperties configuration;

    /**
     * Canonical 2.x accessor. Declared explicitly because Lombok treats the legacy
     * getMapperscanpackages()/setMapperscanpackages() methods as an accessor-name
     * collision and may therefore skip generation for mapperScanPackages.
     */
    public List<String> getMapperScanPackages() {
        return mapperScanPackages;
    }

    /**
     * Canonical 2.x mutator. See {@link #getMapperScanPackages()}.
     */
    public void setMapperScanPackages(List<String> value) {
        this.mapperScanPackages = value;
    }

    /**
     * @deprecated Legacy 1.x JavaBean accessor retained for source/binary compatibility.
     *             Use {@link #getMapperScanPackages()}.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public List<String> getMapperscanpackages() {
        return mapperScanPackages;
    }

    /**
     * @deprecated Legacy 1.x JavaBean accessor retained for source/binary compatibility.
     *             Use {@link #setMapperScanPackages(List)}.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public void setMapperscanpackages(List<String> value) {
        this.mapperScanPackages = value;
    }
}

