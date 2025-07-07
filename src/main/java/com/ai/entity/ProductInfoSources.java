package com.ai.entity;

import java.util.HashSet;
import java.util.Set;

public class ProductInfoSources {

    // 从用户输入直接提取的产品ID
    private Set<String> fromUserInput = new HashSet<>();

    // 从用户会话补充的产品ID
    private Set<String> fromUserSession = new HashSet<>();

    // 获取全部产品ID（合并集合）
    public Set<String> getAllProducts() {
        Set<String> all = new HashSet<>(fromUserInput);
        all.addAll(fromUserSession);
        return all;
    }

    // Getters
    public Set<String> getFromUserInput() { return fromUserInput; }
    public Set<String> getFromUserSession() { return fromUserSession; }
}
