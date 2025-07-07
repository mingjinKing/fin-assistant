package com.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

// 推荐基金对象
@Data
@AllArgsConstructor
public class RecommendedFund {
    private FundCandidate fund;
    private double recommendationScore; // 0-100
}
