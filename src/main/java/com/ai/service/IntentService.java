package com.ai.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IntentService {

    // 简单规则匹配实现 (生产环境可替换为BERT模型)
    public String detectIntent(String userInput) {
        Map<String, List<String>> intentKeywords = new HashMap<>();
        intentKeywords.put("detail_query", Arrays.asList("查", "找", "详情", "是什么", "获取"));
        intentKeywords.put("statistics", Arrays.asList("统计", "总数", "多少", "数量"));
        intentKeywords.put("filter", Arrays.asList("筛选", "哪些", "哪些数据"));
        intentKeywords.put("ranking", Arrays.asList("排名", "最", "高", "低"));
        intentKeywords.put("trend", Arrays.asList("趋势", "变化", "如何", "如何变化"));
        intentKeywords.put("comparison", Arrays.asList("对比", "如何比"));

        for (Map.Entry<String, List<String>> entry : intentKeywords.entrySet()) {
            if (entry.getValue().stream().anyMatch(userInput::contains)) {
                return entry.getKey();
            }
        }
        return "detail_query"; // 默认意图
    }
}
