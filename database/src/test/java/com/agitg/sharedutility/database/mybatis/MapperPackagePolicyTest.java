package com.agitg.sharedutility.database.mybatis;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MapperPackagePolicyTest {
 @Test void preservesOrderAndCommaSeparation() {
  assertEquals("a.mapper,b.mapper",MapperPackagePolicy.requireBasePackage(
       List.of("a.mapper","b.mapper"),"pg.mybatis.mapper-scan-packages"));
 }
 @Test void emptyMaintainsLegacyException() {
  var e=assertThrows(IllegalArgumentException.class, () ->
      MapperPackagePolicy.requireBasePackage(List.of(),"pg.mybatis.mapper-scan-packages"));
  assertEquals("At least one of 'pg.mybatis.mapper-scan-packages' must be specified.",e.getMessage());
 }
 @Test void nullIsRejected() {
  assertThrows(NullPointerException.class, () -> MapperPackagePolicy.requireBasePackage(null,"p"));
 }
}
