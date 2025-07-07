package com.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sql_template_mapping") // MP 表名注解
public class SqlTemplateEntity {
    @TableId(type = IdType.AUTO) // MP 主键策略
    private Integer id;

    @TableField("question") // 字段映射
    private String question;

    @TableField("sql_template") // 字段映射
    private String sqlTemplate;

    @TableField("intent_tag")
    private String intentTag;

    @TableField("description") // 字段映射
    private String description;

    @TableField(exist = false)
    private Float score;
}
