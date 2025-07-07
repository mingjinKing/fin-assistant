package com.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ChartDataset {
    private String label;
    private List<Object> data;
    private String backgroundColor;
}
