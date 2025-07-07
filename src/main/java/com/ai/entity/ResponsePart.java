package com.ai.entity;

import lombok.Data;

// 响应部分
@Data
public class ResponsePart {
    private String type; // chart/table/text
    private String title;
    private Object data; // ChartData/TableData等
}
