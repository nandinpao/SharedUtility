package com.agitg.database.it.mapper;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("routing_record")
public class RouteRecord {
    @TableId
    private Long id;
    private String name;

    public RouteRecord() { }
    public RouteRecord(Long id, String name) { this.id = id; this.name = name; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
