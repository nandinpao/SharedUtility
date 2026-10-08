package com.agitg.sharedutility.database.mybatis;

import java.util.List;
import java.util.Objects;

/** Pure scanner configuration policy; retains the legacy comma-separated package semantics. */
public final class MapperPackagePolicy {
    private MapperPackagePolicy() { }
    public static String requireBasePackage(List<String> packages, String property) {
        Objects.requireNonNull(packages, "packages");
        if (packages.isEmpty()) {
            throw new IllegalArgumentException(
                "At least one of '" + property + "' must be specified.");
        }
        return String.join(",", packages);
    }
}
