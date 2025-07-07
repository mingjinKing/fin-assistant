package com.ai.entity;

import lombok.Data;

import java.util.List;

// 响应对象
@Data
public class AssistantResponse {
    private String type; // composite/simple
    private String textResponse;
    private List<ResponsePart> parts;
}
