package com.ai.entity;

import com.ai.config.SessionManager;
import com.ai.config.SpringContextUtil;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
public class UserProfile {
    
    private String userId;

    private String riskPreference;

    private String investmentGoal;

    private int expectInvestTime;

    private List<Map<String, Object>> holdings;

    private int age;

    private String riskTolerance; // 1-5级
    private List<String> preferredFundTypes; // 偏好基金类型

    private String languagePreference; // 语言风格

    private BigDecimal monthlyExpenseLastMonth; // 近一个月支出

    private String holdingPeriod; // 持仓周期

    private boolean isLogin;

    public UserProfile(String userId, String riskPreference, String investmentGoal, int expectInvestTime, List<String> preferredFundTypes, String riskTolerance) {
        this.userId = userId;
        this.riskPreference = riskPreference;
        this.investmentGoal = investmentGoal;
        this.expectInvestTime = expectInvestTime;
        this.preferredFundTypes = preferredFundTypes;
        this.riskTolerance = riskTolerance;
    }

    // 示例构造方法
    public UserProfile(String userId) {
        this.userId = userId;
        // 模拟数据 - 实际应从数据库获取
        this.riskTolerance = "R3"; // 中等风险
        this.preferredFundTypes = Arrays.asList("股票型", "混合型");
    }

    public boolean isAuthorized() {
        /*SessionManager sessionManager = (SessionManager)SpringContextUtil.getBean("sessionManager");
        UserSession userSession = sessionManager.getOrCreateSession("", userId);
        userSession.getUserProfile()*/
        return isLogin;
    }
}
