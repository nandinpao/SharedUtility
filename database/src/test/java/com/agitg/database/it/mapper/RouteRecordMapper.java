package com.agitg.database.it.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RouteRecordMapper extends BaseMapper<RouteRecord> {
    @Select("SELECT marker FROM route_marker")
    String marker();
}
