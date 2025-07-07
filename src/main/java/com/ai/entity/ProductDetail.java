package com.ai.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 产品详情实体类
 * 包含金融产品的所有关键属性和分析维度
 */
@Data
public class ProductDetail {
    // 基础信息
    private String id;                 // 产品唯一标识符
    private String name;               // 产品名称
    private String type;               // 产品类型 (如: 固收类、权益类等)
    private String issuer;             // 发行机构
    private LocalDate issueDate;       // 发行日期
    private LocalDate maturityDate;    // 到期日期

    // 风险属性
    private String riskLevel;          // 风险等级 (R1-R5)
    private int marketRisk;            // 市场风险等级 (1-5)
    private int creditRisk;            // 信用风险等级 (1-5)
    private String liquidity;          // 流动性 (高/中/低)
    private String riskControlMeasures;// 风控措施

    // 收益属性
    private BigDecimal expectedYield;  // 预期年化收益率
    private BigDecimal historicalYield;// 历史年化收益率
    private BigDecimal minYield;       // 最低预期收益率
    private BigDecimal maxYield;       // 最高预期收益率

    // 投资属性
    private BigDecimal minPurchaseAmount;   // 起购金额
    private BigDecimal purchaseIncrement;   // 申购递增单位
    private BigDecimal redemptionFee;       // 赎回费率
    private BigDecimal managementFee;       // 管理费率
    private String lockupPeriod;            // 锁定期

    // 资产配置
    private String assetClass;              // 资产类别
    private BigDecimal stockPercentage;     // 股票占比
    private BigDecimal bondPercentage;      // 债券占比
    private BigDecimal cashPercentage;      // 现金占比
    private BigDecimal alternativePercentage; // 另类资产占比

    // 分析维度
    private String performanceBenchmark;    // 业绩比较基准
    private String sharpeRatio;             // 夏普比率
    private String maxDrawdown;             // 最大回撤
    private String volatility;              // 波动率

    // 市场数据
    private BigDecimal netAssetValue;       // 最新净值
    private LocalDate navDate;              // 净值日期
    private BigDecimal dailyChange;         // 日涨跌幅

    // 产品亮点
    private List<String> keyFeatures;       // 产品核心亮点

    /**
     * 风险适配状态
     * @param userTolerance 用户风险承受能力等级
     * @return 适配状态描述
     */
    public String getRiskAdaptationStatus(int userTolerance) {
        int productRisk = Math.max(marketRisk, creditRisk);
        if (userTolerance > productRisk) return "✅ 适配";
        if (userTolerance == productRisk) return "⚠️ 临界";
        return "❗️ 不匹配";
    }

    /**
     * 流动性适配状态
     * @param userRequirement 用户流动性需求
     * @return 适配状态描述
     */
    public String getLiquidityAdaptation(String userRequirement) {
        if ("高".equals(liquidity) && !"每日".equals(userRequirement)) return "✅ 充足";
        if ("中".equals(liquidity) && "每周".equals(userRequirement)) return "⚠️ 适中";
        return "❗️ 不足";
    }

    /**
     * 获取产品摘要信息
     * @return 产品摘要字符串
     */
    public String getSummary() {
        return String.format("%s (ID: %s) | 类型: %s | 风险等级: %s | 预期收益: %.2f%%",
                name, id, type, riskLevel, expectedYield.multiply(BigDecimal.valueOf(100)));
    }

    /**
     * 获取详细产品信息
     * @return 产品详细信息字符串
     */
    public String getDetailedInfo() {
        return String.format(" ### 产品详情\n" +
                "                **名称**: %s (ID: %s)\n" +
                "                **类型**: %s\n" +
                "                **发行机构**: %s\n" +
                "                **风险等级**: %s\n" +
                "                **预期收益率**: %.2f%%\n" +
                "                **起购金额**: %,.2f元\n" +
                "                **资产类别**: %s\n" +
                "                **流动性**: %s\n" +
                "                **锁定期**: %s\n" +
                "                **最新净值**: %,.4f (更新日期: %s)",
                name, id, type, issuer, riskLevel,
                expectedYield.multiply(BigDecimal.valueOf(100)),
                minPurchaseAmount, assetClass, liquidity,
                lockupPeriod, netAssetValue, navDate);
    }
}
