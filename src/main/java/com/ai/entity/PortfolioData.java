package com.ai.entity;

import lombok.Data;
import lombok.NoArgsConstructor; // 新增导入
import com.fasterxml.jackson.annotation.JsonCreator; // 新增导入
import com.fasterxml.jackson.annotation.JsonProperty; // 新增导入
import java.util.List;

@Data
public class PortfolioData {
    private String userId;
    private double totalAssets;
    private double marketVolatility;
    private double industryAverageReturn;
    private List<Product> products;

    private List<String> topIndustries; // 持仓前三行业
    private String topHolding; // 最大持仓标的
    private double industryConcentration; // 行业集中度
    private double portfolioVolatility; // 组合波动率
    private double accountSize; // 账户规模(万元)
    private double cashRatio; // 现金比例
    private double stockPosition; // 股票仓位

    public PortfolioData() {}

    @Data
    @NoArgsConstructor // 添加无参构造
    public static class Product {
        private String id;
        private String name;
        private String type;
        private double value;
        private double percentage;
        private double annualizedReturn;
        private String riskLevel;

        // 添加@JsonCreator注解解决反序列化问题
        @JsonCreator
        public Product(
                @JsonProperty("id") String id,
                @JsonProperty("name") String name,
                @JsonProperty("type") String type,
                @JsonProperty("value") double value,
                @JsonProperty("percentage") double percentage,
                @JsonProperty("annualizedReturn") double annualizedReturn,
                @JsonProperty("riskLevel") String riskLevel)
        {
            this.id = id;
            this.name = name;
            this.type = type;
            this.value = value;
            this.percentage = percentage;
            this.annualizedReturn = annualizedReturn;
            this.riskLevel = riskLevel;
        }
    }

    // 原构造方法保持不变
    public PortfolioData(String userId, double totalAssets, double marketVolatility,
                         double industryAverageReturn, List<Product> products) {
        this.userId = userId;
        this.totalAssets = totalAssets;
        this.marketVolatility = marketVolatility;
        this.industryAverageReturn = industryAverageReturn;
        this.products = products;
    }

    // build方法保持不变
    public PortfolioData build() {
        List<PortfolioData.Product> products = new java.util.ArrayList<>();
        products.add(new PortfolioData.Product("201312", "股票A", "股票", 5000, 0.5, 0.1, "R1"));
        products.add(new PortfolioData.Product("543012", "债券B", "债券", 3000, 0.3, 0.05, "R3"));
        products.add(new PortfolioData.Product("123012", "基金C", "基金", 2000, 0.2, 0.08, "R4"));

        return new PortfolioData(
                "user123",
                10000,
                0.2,
                0.07,
                products
        );
    }
}