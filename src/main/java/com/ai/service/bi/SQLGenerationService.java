package com.ai.service.bi;

import com.ai.entity.Intent;
import com.ai.entity.UserRequest;
import com.ai.util.CommonUtils;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionModel;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionRequest;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class SQLGenerationService {

    private final DeepSeekClient deepSeekClient;
    private final SchemaRetrievalService schemaRetrievalService;
    private final JdbcTemplate jdbcTemplate;


    public SQLGenerationService(DeepSeekClient deepSeekClient,
                                SchemaRetrievalService schemaRetrievalService,
                                JdbcTemplate jdbcTemplate) {
        this.deepSeekClient = deepSeekClient;
        this.schemaRetrievalService = schemaRetrievalService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据用户问题生成SQL查询
     */
    public String generateSQL(Intent intent, UserRequest userRequest) {
        // 1. 检索相关表结构
        String relevantSchemas = schemaRetrievalService.getRelevantSchemas(userRequest.getMessage());

        // 2. 构建动态Prompt
        String prompt = String.format("# 数据库表结构\n" +
                "            %s\n" +
                "            \n" +
                "            # 任务说明\n" +
                "            你是一位专业的金融数据分析师，请根据以上表结构将用户问题转换为SQL查询。\n" +
                "            遵守以下规则：\n" +
                "            1. 只使用提供的表\n" +
                "            2. 禁止使用DELETE/UPDATE\n" +
                "            3. 若涉及金额，结果保留两位小数\n" +
                "            4. 当前用户ID：%s\n" +
                "            5. 日期函数使用CURRENT_DATE获取当前日期\n" +
                "            6. 表名和字段名使用大写\n" +
                "            7. 不要添加任何额外解释，只需返回SQL\n" +
                "            8. 根据用户意图：%s，可执行联表查询\n" +
                "            \n" +
                "            # 用户问题\n" +
                "            %s\n" +
                "            \n" +
                "            # SQL查询", relevantSchemas, userRequest.getUserId(), intent.getType(), userRequest.getMessage());

        // 3. 调用大模型生成SQL
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                // 根据渠道模型名称动态修改这个参数
                .model(ChatCompletionModel.DEEPSEEK_CHAT)
                .addUserMessage(prompt).build();
        ChatCompletionResponse response = deepSeekClient.chatCompletion(request).execute();

        return response.choices().get(0).message().content();
    }

    /**
     * 安全执行SQL查询
     */
    public List<Map<String, Object>> executeSafeSQL(String mdSql) {
        String sql = CommonUtils.extractSqlFromMarkdown(mdSql, "sql");
        // 1. sql 语法校验
        if (!sql.matches("^[a-zA-Z0-9_ ]+$")) {
            throw new SecurityException("非法SQL操作");
        }

        // 2. 校验SQL安全性
        if (!isSafeSQL(sql)) {
            throw new SecurityException("非法SQL操作");
        }

        // 3. 执行查询
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * SQL安全校验
     */
    private boolean isSafeSQL(String sql) {
        String normalized = sql.trim().toUpperCase();


        // 仅允许SELECT
        if (!normalized.startsWith("SELECT")) {
            return false;
        }

        // 禁用危险操作
        String[] forbiddenKeywords = {
                "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "GRANT",
                "REVOKE", "CREATE", "ALTER", "EXEC", "XP_", "SHUTDOWN"
        };

        for (String keyword : forbiddenKeywords) {
            if (normalized.contains(keyword)) {
                return false;
            }
        }

        // 禁用危险函数
        String[] forbiddenFunctions = {
                "SLEEP", "BENCHMARK", "UPDATEXML", "LOAD_FILE", "OUTFILE", "DUMPFILE"
        };

        for (String func : forbiddenFunctions) {
            if (normalized.contains(func + "(")) {
                return false;
            }
        }

        return true;
    }
}
