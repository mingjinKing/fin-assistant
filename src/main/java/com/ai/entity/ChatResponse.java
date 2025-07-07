package com.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private String sessionId;
    private String currentAgent;
}