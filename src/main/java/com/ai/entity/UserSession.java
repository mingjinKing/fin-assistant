package com.ai.entity;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.Data;

import java.util.*;

@Data
public class UserSession {

    private String history;

    private String stage;

    private AgentType currentAgent;

    private List<String> comparedProducts;

    private List<String> userInterestedProduct;

    private String sessionId;

    private Map<String, List<UserConversation>> conversationHistory = new HashMap<>();

    private Set<String> selectedProductIds = new HashSet<>();

    private UserProfile userProfile;

    private List<Map<String, Object>> portfolioData;

    private Long lastAccessTime;

    public UserSession(String newSessionId, String userId) {
        this.sessionId = newSessionId;
        if(ObjUtil.isEmpty(this.userProfile)){
            this.userProfile = new UserProfile(userId,"","",0, new ArrayList<>(),"R1");
        }
    }


    // 添加交互记录：动态更新记忆
    public void addInteraction(String userId, String userInput, String agentResponse) {
        List<UserConversation> currentConversation = conversationHistory.getOrDefault(userId, new ArrayList<>());

        // 自动裁剪历史记录，避免过长
        if (currentConversation.size() > 20) {
            currentConversation.subList(
                    conversationHistory.size() - 10,
                    conversationHistory.size()
            );
        }
        currentConversation.add(new UserConversation(userInput, agentResponse));
        conversationHistory.put(userId, currentConversation);
    }

    public List<UserConversation>  getInteractionHistory(String userId){
        return conversationHistory.getOrDefault(userId, Collections.emptyList());
    }

    public void addInterestedProduct(String productId){
        if(ObjectUtil.isEmpty(userInterestedProduct)){
            userInterestedProduct = new ArrayList<>();
            userInterestedProduct.add(productId);
        }
        userInterestedProduct.add(productId);
    }

    // 获取最近5条对话历史（用于Agent上下文）
    public String getRecentHistory(String userId) {
        StringBuilder history = new StringBuilder();
        List<UserConversation> conversationHistory = getInteractionHistory(userId);
        int start = Math.max(0, conversationHistory.size() - 5);
        for (int i = start; i < conversationHistory.size(); i++) {
            UserConversation conversation = conversationHistory.get(i);
            history.append("用户输入: ").append(conversation.getUserInput()).append("\n");
            history.append("Agent 返回值: ").append(conversation.getAgentResponse()).append("\n");
        }
        return history.toString();
    }
}
