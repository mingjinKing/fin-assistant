package com.ai.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

// 提取转义方法到工具类
public class ValueEscapeUtil {
    public static String escapeValue(Object value, String valueType) {
        if (value == null) {
            return "NULL";
        }

        // 处理数组和列表类型（如IN操作符的值）
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            return collection.stream()
                    .map(item -> escapeSingleValue(item, valueType))
                    .collect(Collectors.joining(", ", "(", ")"));
        } else if (value.getClass().isArray()) {
            return Arrays.stream((Object[]) value)
                    .map(item -> escapeSingleValue(item, valueType))
                    .collect(Collectors.joining(", ", "(", ")"));
        }

        return escapeSingleValue(value, valueType);
    }

    private static String escapeSingleValue(Object value, String valueType) {
        if (value == null) {
            return "NULL";
        }

        String strValue = value.toString();

        // 根据值类型进行不同的转义处理
        String type = (valueType != null) ? valueType.toUpperCase() : "STRING";
        switch (type) {
            case "STRING":
                // 转义单引号并包裹在单引号中
                return "'" + strValue.replace("'", "''") + "'";

            case "DATE":
                // 日期类型使用DATE关键字
                return "DATE '" + strValue + "'";

            case "DATETIME":
            case "TIMESTAMP":
                // 日期时间类型
                return "TIMESTAMP '" + strValue + "'";

            case "BOOLEAN":
            case "BOOL":
                // 布尔值不转义
                return Boolean.parseBoolean(strValue) ? "TRUE" : "FALSE";

            case "NUMBER":
            case "INT":
            case "DECIMAL":
            case "FLOAT":
            case "DOUBLE":
                // 数字类型不转义
                return strValue;

            case "LIST":
                // 列表类型特殊处理
                if (value instanceof Collection) {
                    Collection<?> collection = (Collection<?>) value;
                    return collection.stream()
                            .map(item -> escapeSingleValue(item, "STRING"))
                            .collect(Collectors.joining(", ", "(", ")"));
                }
                return strValue;

            case "NULL":
                return "NULL";

            case "COLUMN":
                // 列名不转义，直接使用
                return strValue;

            case "FUNCTION":
                // 函数调用不转义
                return strValue;

            default:
                // 尝试推断类型
                if (isNumeric(strValue)) {
                    return strValue;
                } else if (isBoolean(strValue)) {
                    return Boolean.parseBoolean(strValue) ? "TRUE" : "FALSE";
                } else if (isDate(strValue)) {
                    return "DATE '" + strValue + "'";
                }
                // 默认按字符串处理
                return "'" + strValue.replace("'", "''") + "'";
        }
    }

    // 辅助方法：检查字符串是否为数字
    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 辅助方法：检查字符串是否为布尔值
    private static boolean isBoolean(String str) {
        return "true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str);
    }

    // 辅助方法：检查字符串是否为日期
    private static boolean isDate(String str) {
        // 简单的日期格式检查
        return str.matches("\\d{4}-\\d{2}-\\d{2}");
    }
}
