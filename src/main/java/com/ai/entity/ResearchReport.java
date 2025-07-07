package com.ai.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResearchReport {

    private String reportId;
    private String title;
    private String industry;
    private String content;
    private Float confidenceScore;
    private String filePath;
    private String summary;
}
