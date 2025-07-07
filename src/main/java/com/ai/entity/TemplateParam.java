package com.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TemplateParam {
    private String placeholder; // 占位符名称
    private ParamType type;     // 参数类型
    private Object original;    // 原始值（可选）
    private int position;       // 在SQL中的位置
}
