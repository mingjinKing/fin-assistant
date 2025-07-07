package com.ai.service.bi;

import com.ai.entity.ParamType;
import com.ai.entity.SqlTemplate;
import com.ai.entity.TemplateParam;
import com.ai.util.SqlTemplateExtractor;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SqlTemplateService {


    // 提取模板
    public SqlTemplate extractTemplate(String sql) {
        return SqlTemplateExtractor.extractTemplate(sql);
    }

    // 模板相似性比较
    public boolean isSimilarTemplate(SqlTemplate t1, SqlTemplate t2, double threshold) {
        double similarity = SqlTemplateExtractor.calculateSimilarity(t1, t2);
        return similarity >= threshold;
    }

    // 从模板生成SQL
    public String generateSqlFromTemplate(SqlTemplate template, Map<String, Object> params) {
        String result = template.getTemplate();

        for (TemplateParam param : template.getParams()) {
            String value = formatParamValue(param, params.get(param.getPlaceholder()));
            result = result.replace(param.getPlaceholder(), value);
        }

        return result;
    }

    // 格式化参数值
    private String formatParamValue(TemplateParam param, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("缺少参数值: " + param.getPlaceholder());
        }

        switch (param.getType()) {
            case STRING:
                return "'" + value.toString().replace("'", "''") + "'";

            case DATE:
                if (value instanceof java.util.Date) {
                    return "'" + new SimpleDateFormat("yyyy-MM-dd").format(value) + "'";
                }
                return "'" + value.toString() + "'";

            case DATETIME:
                if (value instanceof java.util.Date) {
                    return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value) + "'";
                }
                return "'" + value.toString() + "'";

            case LIST:
                if (value instanceof Collection) {
                    Collection<?> collection = (Collection<?>) value;
                    return collection.stream()
                            .map(v -> formatParamValue(new TemplateParam("", ParamType.STRING, null, 0), v))
                            .collect(Collectors.joining(", ", "(", ")"));
                }
                throw new IllegalArgumentException("列表参数必须是集合类型: " + param.getPlaceholder());

            case SUBQUERY:
                return value.toString();

            case EXPRESSION:
                return value.toString();

            default:
                return value.toString();
        }
    }

    // 合并相似模板
    public SqlTemplate mergeTemplates(List<SqlTemplate> templates) {
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("模板列表不能为空");
        }

        // 找到最完整的模板
        SqlTemplate baseTemplate = templates.stream()
                .max(Comparator.comparingInt(t -> t.getParams().size()))
                .orElse(templates.get(0));

        // 创建合并后的模板
        SqlTemplate merged = new SqlTemplate();
        merged.setTemplate(baseTemplate.getTemplate());
        merged.setParams(new ArrayList<>(baseTemplate.getParams()));

        // 更新参数占位符为通用名称
        Map<String, String> placeholderMapping = new HashMap<>();
        for (TemplateParam param : merged.getParams()) {
            String genericPlaceholder = "{" + param.getType().name().toLowerCase() + "}";
            placeholderMapping.put(param.getPlaceholder(), genericPlaceholder);
        }

        // 应用通用占位符
        for (Map.Entry<String, String> entry : placeholderMapping.entrySet()) {
            merged.setTemplate(merged.getTemplate().replace(entry.getKey(), entry.getValue()));
        }

        // 更新参数列表
        merged.setParams(merged.getParams().stream()
                .map(p -> new TemplateParam(
                        placeholderMapping.get(p.getPlaceholder()),
                        p.getType(),
                        null,
                        p.getPosition()
                ))
                .collect(Collectors.toList()));

        merged.setNormalizedStructure(SqlTemplateExtractor.normalizeStructure(merged));
        merged.setHash(generateMergedHash(merged));

        return merged;
    }

    private String generateMergedHash(SqlTemplate template) {
        return DigestUtils.md5DigestAsHex(template.getNormalizedStructure().getBytes());
    }


}
