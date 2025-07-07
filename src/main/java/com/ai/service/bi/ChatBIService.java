package com.ai.service.bi;

import com.ai.entity.AssistantResponse;
import com.ai.entity.ProductInfo;
import com.ai.entity.UserProfile;
import com.ai.service.intent.CompositeIntentExecutor;
import com.ai.util.CommonUtils;
import com.ai.util.DeepSeekClientUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Log4j2
public class ChatBIService {

    @Autowired
    private DeepSeekClient deepSeekClient;
    @Autowired
    private SchemaRetrievalService schemaRetrievalService;
    @Autowired
    private JdbcTemplate jdbcTemplate;  // 假设已配置数据库连接
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String sql_query_Reference = "-- 显式KEY-VALUE语法\n" +
            "JSON_OBJECT(KEY 'userId' VALUE u.USER_ID)\n" +
            "\n" +
            "-- 简写语法\n" +
            "JSON_OBJECT('userId': u.USER_ID)\n";

    /**
     * 根据自然语言查询产品信息并返回JSON格式结果
     *
     * @param query 用户查询语句
     * @param profile 用户画像
     * @param jsonFormatExample 期望的JSON格式示例
     * @return JSON格式的查询结果字符串
     */
    public List<String> naturalLanguageQuery(String query, UserProfile profile, String jsonFormatExample, String otherRequirements) {
        List<String> result = new ArrayList<>();
        try {
            // 1. 检索相关表结构
            String relevantSchemas = schemaRetrievalService.getRelevantSchemas(query);

            // 2. 构建大模型提示
            String prompt = buildPrompt(query, relevantSchemas, jsonFormatExample, otherRequirements);
            log.info("chatBi prompt is:{}", prompt);

            // 3. 调用大模型生成SQL
            String generatedSql = DeepSeekClientUtils.getDeepSeekResponseContent( prompt);
            log.info("chatBi generatedSql is:{}", generatedSql);

            // 4. 从 sql 代码块中提取SQL
            String realSql = CommonUtils.extractSqlFromMarkdown(generatedSql, "sql");
            log.info("chatBi realSql is:{}", realSql);

            // 5. 执行SQL查询
            List<Map<String, Object>> queryResults = executeSqlQuery(realSql);
            log.info("chatBi queryResults size is:{}", queryResults.size());

            if(queryResults.isEmpty()) return result;

            queryResults.forEach(map -> {
                String queryRow =new String((byte[])map.get("RESULT")) ;
                result.add(queryRow);
            });

            // 6. 转换为指定JSON格式
            //String result = objectMapper.readValue(jsonResult);
            log.info("chatBi naturalLanguageQuery is:{}", result);
            return result;
        } catch (Exception e) {
            return handleError(e);
        }

    }

    public String genSQLByDeepSeek(String query, String requirement){
        // 1. 检索相关表结构
        String relevantSchemas = schemaRetrievalService.getRelevantSchemas(query);

        // 2. 构建大模型提示
        String prompt = buildPrompt(query, "", "", requirement);

        // 3. 调用大模型生成SQL
        String generatedSql = DeepSeekClientUtils.getDeepSeekResponseContent( prompt);

        // 4. 从 sql 代码块中提取SQL
        String realSql = CommonUtils.extractSqlFromMarkdown(generatedSql, "sql");
        log.info("genSQLByDeepSeek sql is :{}", realSql);
        return realSql;
    }

    /**
     * 构建大模型提示
     */
    private String buildPrompt(String userQuery, String schemas, String jsonFormat, String otherRequirements) {
        return String.format(
                "你是一个SQL生成专家。根据以下信息生成SQL查询：\n" +
                        "### 用户查询：\n%s\n\n" +
                        "### 相关表结构：\n%s\n\n" +
                        "### 严格参考要求的数据格式示例：\n%s\n\n" +
                        "请遵守以下规则：\n" +
                        "1. 只输出SQL语句，不要包含任何其他文本，且只允许 select 语句\n" +
                        "2. 确保字段名与JSON示例中的key完全匹配\n" +
                        "3. 结果字段顺序需与JSON示例一致\n" +
                        "4. 使用标准SQL语法\n" +
                        "5. 数据库类型为H2数据库\n" +
                        "6. 查询结果的最终字段命名为 RESULT\n" +
                        "7. 查询条件尽量使用 like 和或的逻辑关系\n" +
                        "8. 语法可参考：%s \n" +
                        "9. 查询的返回的 item格式化为 JSONArray，可使用函数：JSON_ARRAYAGG\n" +
                        "10. 其他要求：%s",
                userQuery, schemas, jsonFormat, sql_query_Reference, otherRequirements
        );
    }

    /**
     * 执行SQL查询
     */
    private List<Map<String, Object>> executeSqlQuery(String sql) {
        // 添加简单的SQL校验
        String sqlContent = sql.trim().toLowerCase();
        if (sqlContent.startsWith("select")) {
            return jdbcTemplate.queryForList(sql);
        }
        throw new IllegalArgumentException("只允许执行SELECT查询");
    }

    /**
     * 转换为指定JSON格式
     */
    private String convertToJson(List<Map<String, Object>> results, String formatExample)
            throws JsonProcessingException {
        // 此处可根据formatExample进行更复杂的转换
        // 当前实现直接序列化为JSON数组
        return objectMapper.writeValueAsString(results);
    }

    /**
     * 错误处理
     */
    private List<String> handleError(Exception e) {
        List<String> result = new ArrayList<>();
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "查询处理失败");
        errorResponse.put("reason", e.getMessage());
        try {
            String errorMsg = objectMapper.writeValueAsString(errorResponse);
            result.add(errorMsg);
        } catch (JsonProcessingException ex) {
            result.add("{\"error\":\"序列化错误\"}");
        }
        return result;
    }
}