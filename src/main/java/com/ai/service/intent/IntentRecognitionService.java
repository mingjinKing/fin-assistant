package com.ai.service.intent;

import com.ai.entity.Conversation;
import com.ai.entity.Intent;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

// 意图识别服务
@Service
public class IntentRecognitionService {

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private MilvusConversationService milvusConversationService;

    public List<Intent> recognizeIntents(String userQuery, String userId) {
        // 1. 基础意图识别
        List<Intent> intents = primaryIntentRecognition(userQuery);

        // 2. 上下文增强
        enhanceWithContext(intents,userQuery, userId);

        // 3. 参数提取
       // extractParameters(intents, userQuery);

        return intents;
    }

    private List<Intent> primaryIntentRecognition(String userQuery) {
        String prompt = "用户问题：" + userQuery + "\n" +
                "请识别以下意图（可多选）：\n" +
                "1. 行业行情分析\n" +
                "2. 基金详情查询\n" +
                "3. 持仓收益查询\n" +
                "4. 基金产品推荐\n" +
                "5. 市场趋势预测\n\n" +
                "输出JSON格式示例：{\"intents\":[{\"type\":\"意图名称\", \"confidence\":0.8}]}";

        String aiResponse = deepSeekClient.chatCompletion(prompt).execute();
        return parseIntents(aiResponse);
    }

    private List<Intent> parseIntents(String aiResponse) {
        return JSON.parseObject(aiResponse).getJSONArray("intents").stream()
                .map(o -> new Intent(((JSONObject) o).getString("type"), ((JSONObject) o).getDouble("confidence")))
                .collect(Collectors.toList());
    }

    private void  enhanceWithContext(List<Intent> intents,String userQuery, String userId) {
        // 从Milvus获取最近3条对话历史
        List<Conversation> history = milvusConversationService.searchSimilarConversations(userId, userQuery, 3);

        // 检测历史意图并补充
        for (Conversation conv : history) {
            if (conv.getIntent() == null || containsIntent(intents, conv.getIntent())) {
                continue;
            }

            // 动态计算置信度（基于相似度分数）
            double distance = conv.getSimilarityScore();
            // 将距离转换为相似度 (0-1范围)
            double similarity = 1 / (1 + distance);
            // 动态置信度 (0.6 ~ 0.85)
            double dynamicConfidence = 0.6 + 0.25 * similarity;
            intents.add(new Intent(conv.getIntent(), dynamicConfidence));
        }

       /* // 用户画像增强
        if (intents.stream().anyMatch(i -> i.getType().contains("推荐"))) {
            intents.add(new Intent("用户画像匹配", 0.9));
        }*/
    }

    private boolean containsIntent(List<Intent> intents, String intent) {
        return intents.stream().anyMatch(i -> i.getType().equals(intent));
    }

    private void extractParameters(List<Intent> intents, String userQuery) {
        String prompt = "用户问题：" + userQuery + "\n" +
                "已知意图：" + intents.stream().map(Intent::getType).collect(Collectors.joining(",")) + "\n" +
                "请提取参数，输出JSON示例：{\"industry\":\"白酒\",\"timeRange\":\"近一月\"}";

        String aiResponse = deepSeekClient.chatCompletion(prompt).execute();
        JSONObject params = JSON.parseObject(aiResponse);

        // 将参数附加到所有相关意图
        intents.forEach(intent -> intent.setParams(params));
    }
}
