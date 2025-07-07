package com.ai.service.intent;

import com.ai.entity.AssistantResponse;
import com.ai.entity.Intent;
import com.ai.entity.ServiceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 复合意图执行器 - 核心逻辑
@Service
public class CompositeIntentExecutor {

    @Autowired
    private IntentRecognitionService intentRecognitionService;

    @Autowired
    private ServiceRouter serviceRouter;

    @Autowired
    private ResponseGenerator responseGenerator;

    // 异步任务线程池
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(5);

    /**
     *
     * @param userQuery
     * @param userId
     * @return：
     * {
     *   "type": "composite",
     *   "textResponse": "根据您的需求，我为您整理了以下信息：\n\n📈 白酒类基金行情\n- 平均日收益: 0.82%\n- 表现最佳基金收益: 2.15%\n- 相关基金数量: 23\n\n💰 您的持仓基金表现\n- 招商中证白酒指数A: 1250.00元 (12.50%)\n- 鹏华酒ETF联接C: 530.00元 (5.30%)\n",
     *   "parts": [
     *     {
     *       "type": "chart",
     *       "title": "白酒基金行情",
     *       "data": {
     *         "type": "bar",
     *         "labels": ["平均收益", "最佳收益"],
     *         "datasets": [
     *           {"label": "收益率(%)", "data": [0.82, 2.15], "backgroundColor": "#36A2EB"}
     *         ]
     *       }
     *     },
     *     {
     *       "type": "table",
     *       "title": "持仓基金详情",
     *       "data": {
     *         "columns": ["基金名称", "持仓金额", "当前收益", "收益率"],
     *         "rows": [
     *           ["招商中证白酒指数A", "10000.00元", "1250.00元", "12.50%"],
     *           ["鹏华酒ETF联接C", "10000.00元", "530.00元", "5.30%"]
     *         ]
     *       }
     *     }
     *   ]
     * }
     */
    public AssistantResponse execute(String userQuery, String userId) {
        // 1. 意图识别
        List<Intent> intents = intentRecognitionService.recognizeIntents(userQuery, userId);

        // 2. 并行执行服务
        Map<String, ServiceResult> results = executeServicesConcurrently(intents, userId);

        // 3. 生成响应
        return responseGenerator.generateResponse(intents, results);
    }

    private Map<String, ServiceResult> executeServicesConcurrently(List<Intent> intents, String userId) {
        Map<String, CompletableFuture<ServiceResult>> futures = new HashMap<>();
        Map<String, ServiceResult> results = new ConcurrentHashMap<>();

        // 创建并行任务
        for (Intent intent : intents) {
            CompletableFuture<ServiceResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return serviceRouter.route(intent, userId);
                } catch (Exception e) {
                    return new ServiceResult(false, "服务执行失败: " + e.getMessage());
                }
            }, asyncExecutor);

            futures.put(intent.getType(), future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

        // 收集结果
        futures.forEach((type, future) -> {
            try {
                results.put(type, future.get());
            } catch (Exception e) {
                results.put(type, new ServiceResult(false, "结果获取失败"));
            }
        });

        return results;
    }
}
