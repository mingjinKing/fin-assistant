package com.ai.entity;

public enum ParamType {
    NUMBER,         // 数字类型
    STRING,         // 字符串类型
    DATE,           // 日期类型
    DATETIME,       // 日期时间类型
    BOOLEAN,        // 布尔类型
    LIST,           // 列表值
    SUBQUERY,       // 子查询
    EXPRESSION,     // 表达式
    COLUMN,         // 列名
    TABLE,          // 表名
    FUNCTION,       // 函数
    LIMIT_OFFSET,    // LIMIT/OFFSET 参数
    TIME, HEX, UNKNOWN
}
