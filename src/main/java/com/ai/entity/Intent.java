package com.ai.entity;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 意图对象
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Intent {
    private String type;
    private double confidence;
    private JSONObject params;

    public Intent(String userProfile, double v) {
        this.type = userProfile;
        this.confidence = v;
    }
}

