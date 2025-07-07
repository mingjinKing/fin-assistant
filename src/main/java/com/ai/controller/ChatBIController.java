package com.ai.controller;

import com.ai.config.SessionManager;
import com.ai.entity.Intent;
import com.ai.entity.UserRequest;
import com.ai.entity.UserSession;
import com.ai.service.bi.SQLGenerationService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/chatbi")
@Log4j2
public class ChatBIController {

    private final SQLGenerationService sqlGenerationService;

    @Autowired
    private SessionManager sessionManager;

    public ChatBIController(SQLGenerationService sqlGenerationService) {
        this.sqlGenerationService = sqlGenerationService;
    }

    @PostMapping("/query")
    public ResponseEntity<?> handleUserQuery(@RequestHeader(value = "X-Session-Id", required = false) String sessionId,
                                             @RequestBody UserRequest request) {
        UserSession session = sessionManager.getOrCreateSession(sessionId, request.getUserId());
        try {
            // 1. 生成SQL
            String sql = sqlGenerationService.generateSQL(new Intent(), request);
            log.info("generate sql is :{}", sql);

            // 2. 执行查询
            List<Map<String, Object>> result = sqlGenerationService.executeSafeSQL(sql);

            // 3. 格式化返回
            return ResponseEntity.ok(new QueryResult(true, "success", formatResult(result)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new QueryResult(false, "非法操作: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new QueryResult(false, "处理失败: " + e.getMessage(), null));
        }
    }

    /**
     * 格式化查询结果
     */
    private Object formatResult(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return Collections.emptyMap();
        }

        // 简单表格格式
        List<String> columns = new ArrayList<>(data.get(0).keySet());
        List<List<Object>> rows = new ArrayList<>();

        for (Map<String, Object> row : data) {
            rows.add(new ArrayList<>(row.values()));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", "table");
        response.put("columns", columns);
        response.put("data", rows);
        return response;
    }

    // 请求和响应DTO
    @Data
    public static class UserQueryRequest {
        private String query;
    }

    @Data
    @AllArgsConstructor
    public static class QueryResult {
        private boolean success;
        private String message;
        private Object data;
    }
}
