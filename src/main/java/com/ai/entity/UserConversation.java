package com.ai.entity;

import lombok.Data;

@Data
public class UserConversation {

    private String userInput;

    private String agentResponse;

    public UserConversation(String userInput, String agentResponse) {
        this.userInput = userInput;
        this.agentResponse = agentResponse;
    }
}
