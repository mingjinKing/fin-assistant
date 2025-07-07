package com.ai.service.bi;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class QueryClassifierService {

    private final RestTemplate restTemplate;
    private static final String CLASSIFIER_URL = "http://localhost:5000/classify";

    public QueryClassifierService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public String classifyQuery(String query) {
        Map<String, String> request = new HashMap<>();
        request.put("query", query);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    CLASSIFIER_URL,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("class");
            }
        } catch (Exception e) {
            // 降级策略：默认中等复杂度
            return "medium";
        }
        return "medium";
    }
}
