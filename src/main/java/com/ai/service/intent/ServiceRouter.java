package com.ai.service.intent;

import com.ai.entity.Intent;
import com.ai.entity.ServiceResult;
import com.ai.service.MarketAnalysisService;
import com.ai.service.PortfolioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 服务路由
@Service
public class ServiceRouter {

    @Autowired
    private MarketAnalysisService marketAnalysisService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private FundRecommendationService fundRecommendationService;

    public ServiceResult route(Intent intent, String userId) {
        switch (intent.getType()) {
            /*case "行业行情分析":
                return marketAnalysisService.analyzeIndustry(intent);*/

            case "持仓收益查询":
                portfolioService.getPortfolio(userId);
                return null;

            case "基金产品推荐":
                return fundRecommendationService.recommendFunds(intent, userId);

            // 其他服务...

            default:
                return new ServiceResult(false, "未知意图类型: " + intent.getType());
        }
    }
}