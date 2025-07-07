package com.ai.entity;

import lombok.Data;

import java.util.List;

// 图表数据结构
@Data
public class ChartData {
    private String type;
    private List<String> labels;
    private List<ChartDataset> datasets;
}