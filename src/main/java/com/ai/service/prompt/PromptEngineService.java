package com.ai.service.prompt;

import com.ai.entity.UserAssets;
import com.ai.entity.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class PromptEngineService {

    // 核心提示词模板
    private static final String BASE_PROMPT_TEMPLATE =
            "作为智能理财顾问，请基于当前市场环境（%s）和用户专属数据生成分析报告：\n" +
                    "---\n" +
                    "**用户背景**\n" +
                    "风险等级：%s | 投资目标：%s | 持仓周期：%s\n" +
                    "核心持仓：%s（合计占比%.1f%%）\n\n" +
                    "**分析要求**\n" +
                    "1. 市场解读：用%s风格简述今日关键事件（不超过100字）\n" +
                    "2. 持仓关联分析：\n" +
                    "   - 重点提示影响持仓%s的动态\n" +
                    "   - 诊断行业集中风险（若单一行业>30%%需预警）\n" +
                    "3. 个性化策略：\n" +
                    "   - 匹配风险等级的仓位方案\n" +
                    "   - 推荐目标导向的备选标的（说明互补性）\n" +
                    "4. 风险提示：\n" +
                    "   - 量化持仓波动率冲击\n" +
                    "   - 标注未来7天关键事件\n" +
                    "---\n" +
                    "%s"; // 用于追加场景化内容

    // 场景化规则配置
    @Value("${prompt.rules.highRisk:当 risk_level≥C4 & 波动率>基准30%时}")
    private String highRiskRule;

    @Value("${prompt.rules.conservative:当 股票仓位>阈值 & 现金<10%时}")
    private String conservativeRule;

    // 生成完整提示词
    public String generatePrompt(UserProfile profile, UserAssets assetInfo) {
        // 1. 构建基础提示词
        String basePrompt = String.format(
                BASE_PROMPT_TEMPLATE,
                LocalDate.now().toString(),
                profile.getRiskPreference(),
                profile.getInvestmentGoal(),
                profile.getHoldingPeriod(),
                String.join("、", assetInfo.getTopIndustries()),
                assetInfo.getIndustryConcentration(),
                profile.getLanguagePreference(),
                assetInfo.getTopHolding(),
                generateScenarioContent(profile, assetInfo) // 场景化追加内容
        );

        // 2. 添加合规声明
        return basePrompt + "\n\n**合规声明**\n" + getComplianceStatement();
    }

    // 生成场景化追加内容
    private String generateScenarioContent(UserProfile profile, UserAssets assetInfo) {
        StringBuilder scenarioContent = new StringBuilder();

        // 高风险用户场景
        if (isHighRiskScenario(profile, assetInfo)) {
            scenarioContent.append("### 对冲策略建议\n")
                    .append(String.format("- 估算对冲成本（账户规模:%.1f万）\n", assetInfo.getAccountSize()))
                    .append("- 列举相关ETF期权/期货方案\n");
        }

        // 保守型用户现金不足场景
        if (isConservativeCashScenario(profile, assetInfo)) {
            scenarioContent.append("### 现金管理警报\n")
                    .append(String.format("- 紧急备用金缺口:%.1f月支出\n",
                            calculateCashGap(profile, assetInfo)))
                    .append("- 国债逆回购配置建议\n");
        }

        // 行业集中度警告
        if (assetInfo.getIndustryConcentration().compareTo(new BigDecimal(50)) > 0) {
            scenarioContent.append("### 集中度预警\n")
                    .append(String.format("- 前三行业集中度已达%.1f%%\n", assetInfo.getIndustryConcentration()))
                    .append("- 建议分散至相关性<0.3的领域\n");
        }

        return scenarioContent.toString();
    }

    // 高风险场景判断
    private boolean isHighRiskScenario(UserProfile profile, UserAssets assetInfo) {
        return "R4".equals(profile.getRiskPreference()) || "R5".equals(profile.getRiskPreference())
               && assetInfo.getPortfolioVolatility().doubleValue() > 1.3 * getMarketVolatility();
    }

    // 保守用户现金不足判断
    private boolean isConservativeCashScenario(UserProfile profile, UserAssets assetInfo) {
        return "R1".equals(profile.getRiskPreference()) || "R2".equals(profile.getRiskPreference())
               && assetInfo.getCashRatio().doubleValue() < 0.1
                && assetInfo.getStockPosition().doubleValue() > Double.parseDouble(profile.getRiskTolerance().substring(1, 2));
    }

    // 现金缺口计算
    private BigDecimal calculateCashGap(UserProfile profile, UserAssets assetInfo) {
        BigDecimal requiredCash = profile.getMonthlyExpenseLastMonth().multiply(new BigDecimal(3)); // 3个月支出
        BigDecimal currentCash = assetInfo.getTotalAssets().multiply(assetInfo.getCashRatio());
        return (requiredCash.subtract(currentCash)).divide(profile.getMonthlyExpenseLastMonth()) ;
    }

    // 获取市场波动率（示例简化）
    private double getMarketVolatility() {
        // 实际应接入市场数据API
        return 0.25; // 默认25%波动率
    }

    // 合规声明
    private String getComplianceStatement() {
        return "*本分析基于用户授权数据生成，不构成投资建议。"
                + "历史收益不代表未来表现，决策前请咨询持牌顾问*";
    }
}
