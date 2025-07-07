package com.ai.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.ai.config.TemplateVersionManager;
import com.ai.entity.*;
import com.ai.service.IntentService;
import com.ai.service.bi.AdvancedSqlTemplateEngine;
import com.ai.service.bi.ChatBIService;
import com.ai.service.bi.SqlTemplateService;
import com.ai.service.bi.TemplateOptimizer;
import com.ai.service.miluvs.SQLFingerPrintService;
import com.ai.util.SqlTemplateExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/sql-generator")
@Log4j2
public class SQLGeneratorController {

    @Autowired
    private AdvancedSqlTemplateEngine advancedSqlTemplateEngine;
    @Autowired
    private SQLFingerPrintService sqlFingerPrintService;
    @Autowired
    private SqlTemplateService templateService;

    @Autowired
    private TemplateVersionManager versionManager;
    @Autowired
    private IntentService intentService;
    @Autowired
    private AdvancedSqlTemplateEngine templateEngine;
    @Autowired
    private ChatBIService chatBIService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private static final float SCORE_THRESHOLD = 0.78f;
    private ObjectMapper mapper = new ObjectMapper();

    String jsonExample = mapper.writeValueAsString(new PortfolioData());

    public SQLGeneratorController() throws JsonProcessingException {
    }


    @PostMapping("/generateSQL")
    public ResponseEntity<Map<String, String>> generateSQL(
            @RequestBody Map<String, String> request) throws InterruptedException {

        String nlq = request.get("query");

        // 1. 意图识别
        String intent = intentService.detectIntent(nlq);

        // 2. 检索
        List<SqlTemplateEntity> candidates = advancedSqlTemplateEngine.findSimilarTemplate(nlq, intent, 3);

        // 重排与过滤
        // 4. 重排序和阈值过滤
        Optional<SqlTemplateEntity> bestMatch = candidates.stream()
                .peek(template -> {
                    // 意图匹配加分 (相同意图+30%)
                    if (template.getIntentTag().equals(intent)) {
                        template.setScore(template.getScore() * 1.3f);
                    }
                })
                .filter(template -> template.getScore() > SCORE_THRESHOLD)
                .max(Comparator.comparing(SqlTemplateEntity::getScore));

        String sqlTemplate = bestMatch.map(SqlTemplateEntity::getSqlTemplate)
                .orElse(null);

        if (sqlTemplate != null) {
            return ResponseEntity.ok(Collections.singletonMap("sql", sqlTemplate));
        }

        // 冷启动处理
        String generatedSQL = sqlFingerPrintService.generateSQL(nlq);
        SqlTemplateEntity sqlTemp = new SqlTemplateEntity();
        sqlTemp.setQuestion(nlq);
        sqlTemp.setSqlTemplate(generatedSQL);
        sqlTemp.setIntentTag(intent);
        advancedSqlTemplateEngine.addNewMapping(sqlTemp);

        return ResponseEntity.ok(Collections.singletonMap("sql", generatedSQL));
    }

    @PostMapping("/generateTemplate")
    public ResponseEntity<SqlTemplate> generateTemplate(@RequestBody String sql) {
        SqlTemplate template = templateService.extractTemplate(sql);

        // 查找相似模板
        SqlTemplate existing = versionManager.getBestMatch(template);
        if (existing != null) {
            return ResponseEntity.ok(existing);
        }

        // 添加新模板
        versionManager.addTemplate(template);
        return ResponseEntity.ok(template);
    }

    @PostMapping("/optimize")
    public ResponseEntity<SqlTemplate> optimizeTemplate(@RequestBody SqlTemplate template) {
        SqlTemplate optimized = TemplateOptimizer.optimizeTemplate(template);
        return ResponseEntity.ok(optimized);
    }

    @PostMapping("/query")
    public List<UserAssets> handleQuery(@RequestBody Map<String, String> request) {
        String userQuery = request.get("query");
        long startTime1 = System.currentTimeMillis();
        // Step1: 大模型生成JSON结构
        SqlQueryModel model = templateEngine.generateSqlModel(userQuery, jsonExample);

        // Step2: 模板渲染SQL
        String sql = templateEngine.render(model);
        log.info("excute time is :{}", System.currentTimeMillis() - startTime1);

        /*long startTime2 = System.currentTimeMillis();
        List<String> portfolioJson = chatBIService.naturalLanguageQuery(
                "获取用户:" + "U1001" + "的完整持仓数据",
                null,
                jsonExample,
                ""
        );
        log.info("excute time is :{}", System.currentTimeMillis() - startTime2);*/
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        log.info("chatBi queryResults is:{}", JSONUtil.toJsonStr(result));

        try {
            // Step3: 验证SQL安全性
            String formatSQL = SqlTemplateExtractor.validate(sql);
            log.info("SQL is: {}", formatSQL);
            List<Map<String, Object>> queryResults = jdbcTemplate.queryForList(formatSQL);
            return  BeanUtil.copyToList(queryResults, UserAssets.class);
        } catch (JSQLParserException e) {
            log.error("SQL {} 语法错误 {}", sql, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "SQL语法错误: " + e.getMessage());
        } catch (DataAccessException e) {
            log.error("SQL {} 执行错误 {}", sql, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "SQL执行错误: " + e.getMostSpecificCause().getMessage());
        }
    }
}
