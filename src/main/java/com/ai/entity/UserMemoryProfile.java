package com.ai.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UserMemoryProfile {
    private String userId;
    private float[] interestVector; // 兴趣向量
    private float[] riskVector;    // 风险偏好向量
    private float[] knowledgeVector; // 知识水平向量
    private LocalDateTime lastUpdated;

    // 动态权重
    private Map<String, Float> dimensionWeights;

    // 业务方法
    public float getInterestScore(String dimension) {
        // 根据维度获取特定兴趣分数
        return new  Float(0);
    }

    public void updateVector(String dimension, float[] newVector, float weight) {
        // 更新指定维度的向量
    }

    // 省略getter/setter
}
