package com.ai.service.bi;

import com.ai.entity.SqlTemplate;
import com.ai.util.SqlTemplateExtractor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SqlTemplateServiceTest {

    @Test
    public void testExtractTemplate() {
        SqlTemplateService service = new SqlTemplateService();

        // 示例SQL 1
        String sql1 = "SELECT user_id, SUM(trans_amount) AS total " +
                "FROM transaction " +
                "WHERE trans_type = '申购' " +
                "  AND trans_time > '2023-01-01' " +
                "  AND amount > 10000 " +
                "GROUP BY user_id " +
                "HAVING total > 50000 " +
                "ORDER BY total DESC " +
                "LIMIT 10";

        SqlTemplate template1 = service.extractTemplate(sql1);
        System.out.println("模板1:");
        System.out.println(template1.getTemplate());
        template1.getParams().forEach(p ->
                System.out.println(p.getPlaceholder() + " : " + p.getType()));

        // 示例SQL 2 (相似但不同值)
        String sql2 = "SELECT user_id, SUM(trans_amount) AS total " +
                "FROM transaction " +
                "WHERE trans_type = '赎回' " +
                "  AND trans_time > '2023-06-01' " +
                "  AND amount > 5000 " +
                "GROUP BY user_id " +
                "HAVING total > 20000 " +
                "ORDER BY total DESC " +
                "LIMIT 5";

        SqlTemplate template2 = service.extractTemplate(sql2);

        // 比较模板相似性
        double similarity = SqlTemplateExtractor.calculateSimilarity(template1, template2);
        System.out.println("模板相似度: " + similarity);
;
        SqlTemplate merged = new SqlTemplate();
        if (similarity > 0.9) {
            System.out.println("模板高度相似，可以合并");
            merged = service.mergeTemplates(Arrays.asList(template1, template2));
            System.out.println("合并后模板:");
            System.out.println(merged.getTemplate());
        }

        // 使用模板生成SQL
        Map<String, Object> params = new HashMap<>();
        params.put("{trans_type}", "申购");
        params.put("{trans_time}", "2024-01-01");
        params.put("{amount}", 15000);
        params.put("{total}", 60000);
        params.put("{limit_offset_0}", 8);

        String generatedSql = service.generateSqlFromTemplate(template1, params);
        System.out.println("生成的SQL:");
        System.out.println(generatedSql);
    }

}