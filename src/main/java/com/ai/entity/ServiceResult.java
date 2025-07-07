package com.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

// 服务结果
@Data
@AllArgsConstructor
public class ServiceResult {
    private boolean success;
    private String message;
    private Object data;

    public ServiceResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}