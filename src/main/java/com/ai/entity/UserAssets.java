package com.ai.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@TableName("user_assets")
public class UserAssets {
    // 主键
    @TableId("asset_id")
    @JsonProperty("assetId")
    private String assetId;

    // 用户ID
    @TableField("user_id")
    @JsonProperty("userId")
    private String userId;

    // 核心资产信息
    @TableField("total_assets")
    @JsonProperty("totalAssets")
    private BigDecimal totalAssets;

    @TableField("market_volatility")
    @JsonProperty("marketVolatility")
    private BigDecimal marketVolatility;

    @TableField("industry_avg_return")
    @JsonProperty("industryAvgReturn")
    private BigDecimal industryAvgReturn;

    @TableField("top_industries")
    @JsonProperty("topIndustries")
    private String topIndustries;

    @TableField("top_holding")
    @JsonProperty("topHolding")
    private String topHolding;

    @TableField("industry_concentration")
    @JsonProperty("industryConcentration")
    private BigDecimal industryConcentration;

    @TableField("portfolio_volatility")
    @JsonProperty("portfolioVolatility")
    private BigDecimal portfolioVolatility;

    // 账户结构信息
    @TableField("account_size")
    @JsonProperty("accountSize")
    private BigDecimal accountSize;

    @TableField("cash_ratio")
    @JsonProperty("cashRatio")
    private BigDecimal cashRatio;

    @TableField("stock_position")
    @JsonProperty("stockPosition")
    private BigDecimal stockPosition;

    // 持仓产品详情
    @TableField("product_code")
    @JsonProperty("productCode")
    private String productCode;

    @TableField("product_name")
    @JsonProperty("productName")
    private String productName;

    @TableField("product_type")
    @JsonProperty("productType")
    private String productType;

    @TableField("holding_value")
    @JsonProperty("holdingValue")
    private BigDecimal holdingValue;

    @TableField("holding_percentage")
    @JsonProperty("holdingPercentage")
    private BigDecimal holdingPercentage;

    @TableField("annualized_return")
    @JsonProperty("annualizedReturn")
    private BigDecimal annualizedReturn;

    @TableField("risk_level")
    @JsonProperty("riskLevel")
    private String riskLevel;
}