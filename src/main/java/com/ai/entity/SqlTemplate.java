package com.ai.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 模板类定义
@Data
public class SqlTemplate {
    private String template;                  // 模板SQL字符串
    private List<TemplateParam> params;       // 模板参数列表
    private String hash;                      // 模板哈希值
    private String normalizedStructure;       // 规范化结构

    public Map<String, ParamType> getParamTypeMap() {
        return params.stream().collect(Collectors.toMap(
                TemplateParam::getPlaceholder,
                TemplateParam::getType
        ));
    }
}
