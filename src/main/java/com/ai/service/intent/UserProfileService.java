package com.ai.service.intent;

import com.ai.entity.UserProfile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 用户画像服务（模拟实现）
@Service
public class UserProfileService {

    // 模拟数据库存储
    private final Map<String, UserProfile> profileDB = new ConcurrentHashMap<>();

    public UserProfileService() {
        // 初始化模拟数据
        profileDB.put("U12345", new UserProfile("U12345"));
        profileDB.put("U67890", new UserProfile("U67890"));
    }

    public UserProfile getUserProfile(String userId) {
        // 实际应从数据库查询
        return profileDB.getOrDefault(userId, new UserProfile(userId));
    }
}

