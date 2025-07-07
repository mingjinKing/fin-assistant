package com.ai.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Conversation {

    private String intent;

    private String id;

    private String userId;

    private Timestamp timestamp;

    private String content;

    private float similarityScore; // 新增字段

    public Conversation(String content, String intent, Long timestamp, float similarityScore) {
        this.content = content;
        this.intent = intent;
        this.timestamp = new Timestamp(timestamp);
        this.similarityScore = similarityScore;
    }
}
