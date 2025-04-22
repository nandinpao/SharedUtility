package com.agitg.database.bean;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DataSourceProp {

    private String name;
    private String url;
    private String username;
    private String password;

}
