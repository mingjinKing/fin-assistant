package com.ai.config;

import com.ai.entity.PortfolioData;
import com.ai.entity.UserProfile;
import com.ai.entity.UserSession;
import com.ai.service.PortfolioService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Component
@Log4j2
public class SessionManager {

    // 使用ConcurrentHashMap存储会话（生产环境应使用Redis等分布式缓存）
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    // 会话有效期（30分钟）
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000;

    // 添加线程池执行器
    private final Executor asyncExecutor = Executors.newFixedThreadPool(4);

    @Autowired
    private PortfolioService portfolioService;

    // 获取或创建会话
    public UserSession getOrCreateSession(String sessionId, String userId) {
        if (sessionId != null && sessions.containsKey(sessionId)) {
            UserSession session = sessions.get(sessionId);
            session.setLastAccessTime(System.currentTimeMillis());
            return session;
        }

        // 创建新会话
        return createNewSession(userId);
    }

    // 创建新会话
    private UserSession createNewSession(String userId) {
        String newSessionId = "SESS-" + UUID.randomUUID().toString();
        UserSession session = new UserSession(newSessionId, userId);

        UserProfile profile = new UserProfile(
                userId,
                "低等风险",
                "财富保值",
                3,
                new ArrayList<>(),
                "R1"
        );

        // 先设置空持仓（或加载状态）
        session.setPortfolioData(new ArrayList<>());
        session.addInteraction(userId, "", "");
        session.setUserProfile(profile);

        sessions.put(newSessionId, session);

        // 异步加载持仓数据
        CompletableFuture.supplyAsync(() -> portfolioService.getPortfolio(userId), asyncExecutor)
                .thenAccept(portfolio -> {
                    // 线程安全更新会话
                    sessions.computeIfPresent(newSessionId, (id, sess) -> {
                        profile.setHoldings(portfolio);
                        sess.setUserProfile(profile);
                        sess.setPortfolioData(portfolio);
                        log.info("更新持仓数据: {}", portfolio);
                        return sess;
                    });
                })
                .exceptionally(ex -> {
                    // 异常处理：记录日志并设置错误状态
                    sessions.computeIfPresent(newSessionId, (id, sess) -> {
                        sess.setPortfolioData(new ArrayList<>());
                        log.error("加载持仓数据失败: {}", ex.getMessage());
                        return sess;
                    });
                    return null;
                });

        return session;
    }

    // 更新会话
    public void updateSession(UserSession session) {
        session.setLastAccessTime(System.currentTimeMillis());
        sessions.put(session.getSessionId(), session);

        // 清理过期会话
        cleanExpiredSessions();
    }

    // 清理过期会话
    private void cleanExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry ->
                currentTime - entry.getValue().getLastAccessTime() > SESSION_TIMEOUT
        );
    }

}
