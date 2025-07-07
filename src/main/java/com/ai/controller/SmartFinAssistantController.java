package com.ai.controller;


import cn.hutool.core.util.ObjUtil;
import com.ai.config.SessionManager;
import com.ai.config.SystemContextManager;
import com.ai.dbOp.ChatRecordRepository;
import com.ai.entity.ChatRecord;
import com.ai.entity.ChatResponse;
import com.ai.entity.UserRequest;
import com.ai.entity.UserSession;
import com.ai.service.AgentOrchestrator;
import com.ai.service.RouterAgent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pigmesh.ai.deepseek.core.chat.AssistantMessage;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import io.github.pigmesh.ai.deepseek.core.chat.Delta;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@RestController
@Log4j2
public class SmartFinAssistantController {

    @Autowired
    private AgentOrchestrator agentOrchestrator;
    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private ChatRecordRepository chatRecordRepository;
    @Autowired
    private SystemContextManager systemContextManager;
    ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/smartFin/initSession")
    public ResponseEntity<?> initSession(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestBody UserRequest request) {
        String userId = request.getUserId();
        UserSession session = sessionManager.getOrCreateSession(sessionId, userId);
        systemContextManager.setContextParams("user_id", userId); // 实际从请求头获取
        return ResponseEntity.ok()
                .header("X-Session-Id", session.getSessionId()).body("init success");
    }

    @PostMapping(value = "/smartFin/assistChat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + "; charset=UTF-8")
    public ResponseEntity<Flux<ChatCompletionResponse>> chat(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestBody UserRequest request) throws Exception {

        // userSession 中管理用户画像与历史对话
        UserSession session = sessionManager.getOrCreateSession(sessionId, request.getUserId());
        log.info("useSession is :{}", session);
        String userMessage = request.getMessage();

        // 创建累积响应内容的容器（线程安全）
        StringBuilder fullResponse = new StringBuilder();
        StringBuilder reasonResponse = new StringBuilder();

        // 处理原始流并添加操作
        // 实时发送给前端的同时累积响应
        Flux<ChatCompletionResponse> responseFlux = agentOrchestrator
                .process(session, userMessage)
                .cast(ChatCompletionResponse.class) // 确保类型为String
                .doOnNext(chunk -> {
                    Delta delta = chunk.choices().get(0).delta();
                    String reasoningContent = ObjUtil.isEmpty(delta) ? "" : delta.reasoningContent();
                    String content = ObjUtil.isEmpty(delta) ? "" : delta.content();
                    if(StringUtils.isNotEmpty(content)) {
                        // 实时发送给前端的同时累积响应
                        fullResponse.append(content);
                        reasonResponse.append(reasoningContent);
                    }
                })
                .doFinally(signal -> {
                    // 流结束时异步保存到数据库（不阻塞响应）
                    if (signal == SignalType.ON_COMPLETE || signal == SignalType.ON_ERROR) {
                        CompletableFuture.runAsync(() -> {
                            saveToDatabase(
                                    session.getSessionId(),
                                    userMessage,
                                    fullResponse.toString()
                            );
                            sessionManager.updateSession(session);
                        });
                    }
                });

        return ResponseEntity.ok()
                .header("X-Session-Id", session.getSessionId())
                //.header("X-Current-Agent", session.getCurrentAgent().name())
                .body(responseFlux);
    }

    // 异步保存到H2数据库
    @Async
    protected void saveToDatabase(String sessionId, String userMessage, String aiResponse) {
        try {
            log.info("aiResponse is :{}", aiResponse);
            // 使用Spring Data JPA示例（需提前定义ChatRecord实体和Repository）
            ChatRecord record = new ChatRecord();
            record.setSessionId(sessionId);
            record.setUserMessage(userMessage);
            record.setAiResponse(aiResponse);
            record.setTimestamp(LocalDateTime.now());

            chatRecordRepository.save(record);
            log.info("Saved chat record for session: {}", sessionId);
        } catch (Exception e) {
            log.info("Failed to save chat record", e);
        }
    }


}
