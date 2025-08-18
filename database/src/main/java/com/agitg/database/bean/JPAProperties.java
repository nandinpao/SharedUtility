package com.agitg.database.bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "pg.jpa")
public class JPAProperties {
    /**
     * 是否啟用此 AutoConfiguration。
     */
    private boolean enabled = false;

    /**
     * 會被合併進 spring.jpa.properties.* 的屬性。
     * YAML 可用階層式結構，例如:
     * jpa.properties.hibernate.format_sql=false
     */
    private Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * 將層級 Map 攤平成 "a.b.c" 風格的扁平 Map，供 Hibernate 屬性使用。
     */
    public Map<String, String> flattenedProperties() {
        Map<String, String> result = new LinkedHashMap<>();
        flatten("", this.properties, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Object value, Map<String, String> out) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = e.getKey().toString();
                flatten(prefix.isEmpty() ? key : prefix + "." + key, e.getValue(), out);
            }
        } else if (value instanceof List<?> list) {
            // 以逗號串起來
            String joined = String.join(",", list.stream().map(String::valueOf).toList());
            out.put(prefix, joined);
        } else if (value != null) {
            out.put(prefix, value.toString());
        }
    }
}
