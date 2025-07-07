package com.ai.service;

import com.ai.entity.AgentType;
import com.ai.entity.UserSession;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.SyncOrAsyncOrStreaming;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionModel;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionRequest;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 路由 agent ：意图识别与路由
@Service
@Log4j2
public class RouterAgent {

    @Autowired
    private DeepSeekClient deepSeekClient;

    public AgentType route(String userInput, UserSession session) {
        // 使用大模型进行意图识别
        String prompt = buildRoutingPrompt(userInput, session);
        log.info("意图识别提示：{}", prompt);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                // 根据渠道模型名称动态修改这个参数
                .model(ChatCompletionModel.DEEPSEEK_CHAT)
                .addUserMessage(prompt).build();

        ChatCompletionResponse response = deepSeekClient.chatCompletion(request).execute();

        String agentType = response.choices().get(0).message().content();
        log.info("路由结果：{}", agentType);
        // 解析响应
        return parseAgentType(agentType);
    }

    private String buildRoutingPrompt(String userInput, UserSession session) {
        String userId = session.getUserProfile().getUserId();
        return String.format("意图识别提示：用户理财旅程路由决策：\n" +
                        "\n" +
                        "**1. 当前会话阶段**：{%s}（需动态更新）\n" +
                        "**2. 用户历史交互**（最近5条）：\n %s" +
                        "**3. 用户画像**：{%s}（重点关注：持仓/经验/目标）\n" +
                        "**4. 当前输入**：\"{%s}\"\n" +
                        "\n" +
                        "**阶段定义与判断规则**：\n" +
                        "1. **探索期（选1）**：目标模糊，行为随机，满足以下任一：\n" +
                        "   - 基础概念询问（如术语解释、政策规则）\n" +
                        "   - 无明确产品指向\n" +
                        "   - 用户画像为新手（持仓=0/未登录）\n" +
                        "2. **认知期（选2）**：明确请求分析产品**（如“分析XX基金”“消费类基金优缺点”）\n" +
                        "3. **决策期（选3）**：直接**对比产品**（如“A和B基金哪个好”）\n" +
                        "4. **行动期（选4）**：明确**请求推荐产品**（如“推荐适合我的基金”）\n" +
                        "\n" +
                        "**优先级规则**：\n" +
                        "- 若当前输入为概念解释类问题 → **强制归为探索期（1）**\n" +
                        "- 用户画像持仓=0且未登录时 → 默认探索期（1），除非明确提及产品\n" +
                        "- 历史阶段仅作参考，以当前输入意图为准\n" +
                        "\n" +
                        "请输出数字：1/2/3/4",
                session.getCurrentAgent() != null ? session.getCurrentAgent().name() : "初始阶段",
                session.getRecentHistory(userId),
                session.getUserProfile(),
                userInput
        );
    }

    private AgentType parseAgentType(String response) {
        try {
            int agentCode = Integer.parseInt(response.trim());
            switch (agentCode){
                case 2:
                    return AgentType.ANALYSIS;
                case 3:
                    return AgentType.DECISION;
                case 4:
                    return AgentType.RECOMMENDATION;
                case 1:
                default:
                    return AgentType.EXPLORATION;
            }
        } catch (NumberFormatException e) {
            return AgentType.EXPLORATION; // 解析失败时回退
        }
    }

}
