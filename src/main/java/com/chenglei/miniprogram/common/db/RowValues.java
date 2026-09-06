package com.chenglei.miniprogram.common.db;

import java.util.Map;

/**
 * MyBatis Map 查询结果的取值工具。
 *
 * H2（DATABASE_TO_LOWER=TRUE）会把列别名统一转小写，而 MySQL 保留别名原样，
 * 因此所有按别名取 Map 值的地方必须走大小写不敏感的 {@link #valueOf}，
 * 否则同一查询在本地 H2 测试与生产 MySQL 上行为不一致。
 */
public final class RowValues {

    private RowValues() { }

    public static Object valueOf(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value != null || values.containsKey(key)) return value;
        return values.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(key))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    public static long number(Map<String, Object> values, String key) {
        Object value = valueOf(values, key);
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    public static String string(Map<String, Object> values, String key) {
        return String.valueOf(valueOf(values, key));
    }
}
