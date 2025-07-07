package com.ai.service;

import cn.hutool.core.util.ObjectUtil;
import com.ai.entity.AgentType;
import com.ai.entity.UserConversation;
import com.ai.entity.UserSession;
import com.ai.util.FluxUtils;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AgentOrchestrator {
    @Autowired
    private RouterAgent routerAgent;
    @Autowired
    private NavigationAgent navigationAgent;
    @Autowired
    private AnalysisAgent analysisAgent;
    @Autowired
    private DecisionAgent decisionAgent;
    @Autowired
    private RecommendationAgent recommendationAgent;

    public Flux<?> process(UserSession session, String userInput) throws Exception{
        String userId = session.getUserProfile().getUserId();
        // 1. 查询用户历史交互
        List<UserConversation> userInterActHis = session.getInteractionHistory(userId);

        // 2. 确定当前Agent类型
        AgentType currentType = determineAgentType(session, userInput);
        session.setCurrentAgent(currentType);

        // 3. 执行对应Agent
        Flux<?> response = executeAgent(currentType, session, userInput);

        // 4. 更新交互历史
        session.addInteraction(userId, userInput, "");

        return response;
    }


    private AgentType determineAgentType(UserSession session, String userInput) {
        // 如果有明确的Agent切换信号
        if (userInput.contains("推荐")) return AgentType.RECOMMENDATION;
        if (userInput.contains("对比") || userInput.contains("比较")) return AgentType.DECISION;
        if (userInput.contains("分析")) return AgentType.ANALYSIS;

        // 如果会话中有选定产品，自动进入分析阶段
        if (session.getUserInterestedProduct() != null) return AgentType.ANALYSIS;

        // 如果已对比过产品，自动进入推荐阶段
        if (ObjectUtil.isNotEmpty(session.getComparedProducts())) return AgentType.RECOMMENDATION;

        // 默认使用路由Agent决策
        return routerAgent.route(userInput, session);
    }

    private Flux<?> executeAgent(AgentType type, UserSession session, String userInput) throws Exception{
        switch (type){
            case EXPLORATION:
                return navigationAgent.handle(userInput, session);
            case ANALYSIS:
                return analysisAgent.handle(userInput, session);
            case DECISION:
                return decisionAgent.handle(userInput, session);
            case RECOMMENDATION:
                return recommendationAgent.handle(userInput, session);
            default:
                return FluxUtils.string2Flux("无法处理该请求");
        }
    }
}
