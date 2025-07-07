package com.ai.entity;

import lombok.Data;

import java.util.List;

// 表格数据结构
@Data
public class TableData {
    private List<String> columns;
    private List<List<Object>> rows;
}