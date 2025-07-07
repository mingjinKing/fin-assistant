package com.ai.service.intent;

import com.ai.entity.*;
import com.ai.service.miluvs.MilvusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FundRecommendationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private MilvusService milvusService;

    // 推荐服务主入口
    public ServiceResult recommendFunds(Intent intent, String userId) {
        try {
            // 1. 获取用户画像
            UserProfile userProfile = userProfileService.getUserProfile(userId);
            if (userProfile == null) {
                return new ServiceResult(false, "用户画像获取失败");
            }

            // 2. 获取行业基金候选集
            List<FundCandidate> candidates = getIndustryCandidates("industry", 50);
            if (candidates.isEmpty()) {
                return new ServiceResult(false, "未找到相关行业基金");
            }

            // 3. 执行推荐算法
            List<RecommendedFund> recommendations = calculateRecommendations(
                    candidates, userProfile);

            // 4. 过滤敏感信息
            List<Map<String, Object>> safeResults = filterSensitiveData(
                    recommendations, userProfile);

            return new ServiceResult(true, "推荐成功", safeResults);
        } catch (Exception e) {
            return new ServiceResult(false, "推荐失败: " + e.getMessage());
        }
    }

    // 获取行业候选基金
    private List<FundCandidate> getIndustryCandidates(String industry, int limit) {
        // 使用Milvus向量搜索增强行业匹配
        List<String> fundCodes = milvusService.searchFundsByIndustry(industry, limit);

        if (fundCodes.isEmpty()) {
            // 降级到SQL查询
            String sql = "SELECT FUND_CODE, FUND_NAME, FUND_TYPE, RISK_LEVEL, " +
                    "ANNUAL_RETURN, MAX_DRAWDOWN, SHARPE_RATIO " +
                    "FROM FUNDS WHERE INDUSTRY LIKE ? LIMIT ?";
            return jdbcTemplate.query(sql, (rs, rowNum) ->
                    new FundCandidate(
                            rs.getString("FUND_CODE"),
                            rs.getString("FUND_NAME"),
                            rs.getString("FUND_TYPE"),
                            rs.getInt("RISK_LEVEL"),
                            rs.getBigDecimal("ANNUAL_RETURN"),
                            rs.getBigDecimal("MAX_DRAWDOWN"),
                            rs.getBigDecimal("SHARPE_RATIO")
                    ), "%" + industry + "%", limit);
        }

        // 根据Milvus结果查询详情
        String inClause = String.join(",", Collections.nCopies(fundCodes.size(), "?"));
        String sql = "SELECT FUND_CODE, FUND_NAME, FUND_TYPE, RISK_LEVEL, " +
                "ANNUAL_RETURN, MAX_DRAWDOWN, SHARPE_RATIO " +
                "FROM FUNDS WHERE FUND_CODE IN (" + inClause + ")";

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new FundCandidate(
                        rs.getString("FUND_CODE"),
                        rs.getString("FUND_NAME"),
                        rs.getString("FUND_TYPE"),
                        rs.getInt("RISK_LEVEL"),
                        rs.getBigDecimal("ANNUAL_RETURN"),
                        rs.getBigDecimal("MAX_DRAWDOWN"),
                        rs.getBigDecimal("SHARPE_RATIO")
                ), fundCodes.toArray());
    }

    // 计算推荐结果
    private List<RecommendedFund> calculateRecommendations(
            List<FundCandidate> candidates, UserProfile userProfile) {

        // 1. 风险匹配过滤
        List<FundCandidate> riskFiltered = candidates.stream()
                .filter(candidate ->
                        Math.abs(candidate.getRiskLevel() - Integer.parseInt(userProfile.getRiskTolerance().substring(1,2))) <= 1)
                .collect(Collectors.toList());

        // 2. 计算推荐分数
        List<RecommendedFund> scoredFunds = riskFiltered.stream()
                .map(candidate -> {
                    double score = calculateFundScore(candidate, userProfile);
                    return new RecommendedFund(candidate, score);
                })
                .collect(Collectors.toList());

        // 3. 排序并取TOP5
        return scoredFunds.stream()
                .sorted((f1, f2) -> Double.compare(f2.getRecommendationScore(), f1.getRecommendationScore()))
                .limit(5)
                .collect(Collectors.toList());
    }

    // 计算基金推荐分数
    private double calculateFundScore(FundCandidate fund, UserProfile userProfile) {
        // 权重配置
        double returnWeight = 0.4;
        double riskWeight = 0.3;
        double consistencyWeight = 0.2;
        double preferenceWeight = 0.1;

        // 1. 收益得分 (0-100)
        double returnScore = normalize(fund.getAnnualReturn().doubleValue(), 5, 30) * 100;

        // 2. 风险得分 (风险越低得分越高)
        double riskScore = 100 - normalize(fund.getMaxDrawdown().doubleValue(), 10, 50) * 100;

        // 3. 稳定性得分 (夏普比率)
        double sharpeScore = normalize(fund.getSharpeRatio().doubleValue(), 0.5, 2.0) * 100;

        // 4. 用户偏好匹配度
        double preferenceScore = 0;
        if (userProfile.getPreferredFundTypes().contains(fund.getFundType())) {
            preferenceScore = 80; // 基础偏好加分
        }

        // 5. 组合得分
        return returnScore * returnWeight +
                riskScore * riskWeight +
                sharpeScore * consistencyWeight +
                preferenceScore * preferenceWeight;
    }

    // 归一化函数
    private double normalize(double value, double min, double max) {
        if (value < min) return 0;
        if (value > max) return 1;
        return (value - min) / (max - min);
    }

    // 过滤敏感数据
    private List<Map<String, Object>> filterSensitiveData(
            List<RecommendedFund> recommendations, UserProfile userProfile) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (RecommendedFund fund : recommendations) {
            Map<String, Object> item = new LinkedHashMap<>();
            FundCandidate candidate = fund.getFund();

            // 基础信息（所有用户可见）
            item.put("fundCode", candidate.getFundCode());
            item.put("fundName", candidate.getFundName());
            item.put("fundType", candidate.getFundType());
            item.put("recommendScore", Math.round(fund.getRecommendationScore()));

            // 风险等级（所有用户可见）
            item.put("riskLevel", formatRiskLevel(candidate.getRiskLevel()));

            item.put("annualReturn", String.format("%.2f%%", candidate.getAnnualReturn()));
            item.put("maxDrawdown", String.format("%.2f%%", candidate.getMaxDrawdown()));
            item.put("sharpeRatio", String.format("%.2f", candidate.getSharpeRatio()));

            result.add(item);
        }

        return result;
    }

    // 辅助方法：格式化风险等级
    private String formatRiskLevel(int level) {
        switch ( level){
            case 1:
                return "低";
            case 2:
                return "中低";
            case 3:
                return "中";
            case 4:
                return "中高";
            case 5:
                return "高";
            default:
                return "未知";
        }
    }

    // 辅助方法：收益评级
    private String getReturnRating(BigDecimal annualReturn) {
        double value = annualReturn.doubleValue();
        if (value > 20) return "⭐⭐⭐⭐⭐";
        if (value > 15) return "⭐⭐⭐⭐";
        if (value > 10) return "⭐⭐⭐";
        if (value > 5) return "⭐⭐";
        return "⭐";
    }

    // 辅助方法：风险评级
    private String getRiskRating(BigDecimal maxDrawdown) {
        double value = maxDrawdown.doubleValue();
        if (value < 10) return "⛱️ 低波动";
        if (value < 20) return "🌊 中波动";
        if (value < 30) return "🌪️ 高波动";
        return "🔥 极高波动";
    }
}

