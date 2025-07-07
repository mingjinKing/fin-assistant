package com.ai.controller;

import com.ai.service.bi.QueryClassifierService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// QueryController.java
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryClassifierService classifierService;

    public QueryController(QueryClassifierService classifierService) {
        this.classifierService = classifierService;
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processQuery(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String complexity = classifierService.classifyQuery(query);

        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("complexity", complexity);
        response.put("timestamp", Instant.now().toString());

        // 后续路由逻辑
        switch(complexity) {
            case "simple":
                response.put("action", "use_template");
                break;
            case "medium":
                response.put("action", "generate_json_sql");
                break;
            case "complex":
                response.put("action", "async_deepseek");
                break;
        }

        return ResponseEntity.ok(response);
    }
}