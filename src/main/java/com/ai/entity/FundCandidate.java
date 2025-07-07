package com.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

// 基金候选对象
@Data
@AllArgsConstructor
public class FundCandidate {
    private String fundCode;
    private String fundName;
    private String fundType;
    private int riskLevel; // 1-5级
    private BigDecimal annualReturn; // 年化收益
    private BigDecimal maxDrawdown; // 最大回撤
    private BigDecimal sharpeRatio; // 夏普比率
}
