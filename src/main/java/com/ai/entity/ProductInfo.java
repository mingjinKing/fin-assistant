package com.ai.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ProductInfo {
    // 公共字段
    private String productCode;
    private String productName;
    private String productDesc;
    private String riskLevel;
    private Double minPurchaseAmount;
    private String assetClass;

    // 产品类型标识
    private String productType;  // "fund" 或 "financial"

    // 基金特有字段
    private String fundType;
    private String fundCompany;
    private String manager;
    private Double netValue;
    private Double accumNetValue;
    private Date establishDate;
    private Double fundSize;
    private Double feeRate;
    private String purchaseStatus;
    private Double dailyReturn;
    private Double weeklyReturn;
    private Double monthlyReturn;
    private Double quarterReturn;
    private Double halfYearReturn;
    private Double annualReturn;

    // 理财特有字段
    private String issuer;
    private String financialProductType;
    private Double expectReturn;
    private Double actualReturn;
    private Integer duration;
    private Date startDate;
    private Date endDate;
    private String riskControl;

    public ProductInfo() {}

    // 基础构造器
    public ProductInfo(String productCode, String productName, String productDesc,
                       String riskLevel, Double minPurchaseAmount) {
        this.productCode = productCode;
        this.productName = productName;
        this.productDesc = productDesc;
        this.riskLevel = riskLevel;
        this.minPurchaseAmount = minPurchaseAmount;
    }
}