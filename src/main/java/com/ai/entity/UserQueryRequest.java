package com.ai.entity;

import lombok.Data;

// 用户请求
@Data
public class UserQueryRequest {
    private String query;
    private String userId;
}