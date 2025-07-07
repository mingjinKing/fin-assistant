package com.ai.service;

import com.ai.entity.ProductInfo;
import com.ai.entity.ProductInfoSources;
import com.ai.entity.UserSession;
import com.ai.service.bi.AdvancedSqlTemplateEngine;
import com.ai.service.bi.ChatBIService;
import com.ai.util.CommonUtils;
import com.ai.util.DeepSeekClientUtils;
import com.ai.util.FluxUtils;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

// 决策教练：为用户决策提供智能分析
@Service
@Log4j2
public class DecisionAgent {
    @Autowired
    private DeepSeekClient deepSeekClient;
    @Autowired
    private ChatBIService chatBIService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AdvancedSqlTemplateEngine sqlTemplateEngine;
    ObjectMapper mapper = new ObjectMapper();

    private static final Set<String> TABLE_FIELDS = new HashSet<>();

    @PostConstruct
    public void init() {
        // 初始化表字段
        TABLE_FIELDS.addAll(Arrays.asList("product_name", "annual_yield", "risk_level",
                "min_investment", "investment_period", "product_type"));
    }


    public Flux<?> handle(String userInput, UserSession session) throws  Exception{
        // 1. 提取产品信息
        String productInfo = extractProductIds(userInput, session);
        if (StringUtils.isBlank(productInfo)) {
            return FluxUtils.string2Flux("请提供需要对比的产品名称或ID");
        }

        String prompt = String.format("查询产品信息：%s \n"+
                "要求：\n" +
                "1. 关联查询时，采用产品 id 或者产品名称，而不是产品类型; \n" +
                "2. 查询产品无需关注用户是否持仓; \n" +
                "3. 禁止出现 DeepSeek"
                , productInfo);
        // 2. 获取产品信息
        String sql = sqlTemplateEngine.genSQL(prompt);
        List<Map<String, Object>> prodInfo = jdbcTemplate.queryForList(sql);
        if (prodInfo.isEmpty()) {
            return FluxUtils.string2Flux("未找到相关产品信息");
        }

        // 3. 生成对比报告
        return generateComparisonReport(prodInfo, session);
    }

    private String extractProductIds(String input, UserSession session) throws Exception {
        // 优化产品提取逻辑
        if (StringUtils.isNotBlank(input)) {
            // 实现更健壮的提取逻辑（伪代码）
            return CommonUtils.extractProductId(input, session);
        }
        // 补充会话中的产品信息
        return "";
    }

    /*private String getUserInterestedProducts(UserSession session) {
        return session != null ? session.getUserInterestedProduct() : "";
    }*/

    private Flux<?> generateComparisonReport(List<Map<String, Object>> products, UserSession session) {
        // 构建对比表格
        String table = buildComparisonTable(products);

        // 获取用户画像信息
        String riskPref = session.getUserProfile() != null ?
                session.getUserProfile().getRiskPreference() : "未设置";
        String investGoal = session.getUserProfile() != null ?
                session.getUserProfile().getInvestmentGoal() : "未设置";

        // 优化后的提示词
        String prompt = String.format(
                "你是一名专业理财顾问，请根据以下信息提供产品PK分析：" +
                        "## 产品对比分析报告\n\n" +
                        "### 产品对比表格\n%s\n\n" +
                        "### 用户画像\n" +
                        "- **风险偏好**: %s\n" +
                        "- **投资目标**: %s\n\n" +
                        "### 分析要求\n" +
                        "请基于以上产品数据和用户画像，完成以下分析：\n" +
                        "1. **核心优势分析**：\n" +
                        "   - 用表格形式列出每个产品的2个核心优势（包含技术优势和市场优势）\n" +
                        "   - 对每个优势进行简短说明（不超过20字）\n\n" +
                        "2. **用户匹配度分析**：\n" +
                        "   - 结合用户风险偏好和投资目标，分析每个产品与用户的匹配程度\n" +
                        "   - 使用星级评分（1-5★）量化匹配度\n\n" +
                        "3. **综合推荐**：\n" +
                        "   - 给出推荐排序及理由（考虑优势、风险、用户适配度）\n" +
                        "   - 针对不同投资场景提供建议（短期/长期/高风险承受等）\n\n" +
                        "4. **风险提示**：\n" +
                        "   - 列出每个产品的关键风险点\n\n" +
                        "### 输出要求\n" +
                        "- 使用专业金融分析语言\n" +
                        "- 关键数据用**加粗**强调\n" +
                        "- 不同产品用不同颜色标记（红色表示高风险，绿色表示低风险）\n" +
                        "- 最终结果用表格汇总展示",
                table, riskPref, investGoal
        );
        log.error("generateComparisonReport prompt is :{}",  prompt);

        return deepSeekClient.chatFluxCompletion(prompt);
    }

    private String buildComparisonTable(List<Map<String, Object>> products) {
        // 动态获取表格列（保留关键字段）
        Set<String> columns = TABLE_FIELDS.stream()
                .filter(field -> products.stream().anyMatch(p -> p.containsKey(field)))
                .collect(Collectors.toSet());

        // 构建表头
        StringBuilder table = new StringBuilder("| 产品名称 ");
        for (String col : columns) {
            if (!"product_name".equals(col)) {
                table.append("| ").append(translateFieldName(col)).append(" ");
            }
        }
        table.append("|\n");

        // 添加分隔线
        table.append(CommonUtils.repeat("|", columns.size())).append("|\n")
                .append("| --- ");
        for (int i = 1; i < columns.size(); i++) {
            table.append("| :---: ");
        }
        table.append("|\n");

        // 填充数据行
        for (Map<String, Object> product : products) {
            String name = product.getOrDefault("product_name", "未知产品").toString();
            table.append("| **").append(name).append("** ");

            for (String col : columns) {
                if (!"product_name".equals(col)) {
                    Object value = product.getOrDefault(col, "N/A");
                    table.append("| ").append(formatValue(col, value)).append(" ");
                }
            }
            table.append("|\n");
        }
        return table.toString();
    }

    private String translateFieldName(String field) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("product_code", "产品代码");
        mapping.put("product_name", "产品名称");
        mapping.put("annual_yield", "预期年化");
        mapping.put("risk_level", "风险等级");
        mapping.put("min_investment", "起投金额");
        mapping.put("investment_period", "投资期限");
        mapping.put("product_type", "产品类型");

        return mapping.getOrDefault(field, field);
    }

    private String formatValue(String field, Object value) {
        if ("annual_yield".equals(field)) {
            return value + "%";
        }
        if ("min_investment".equals(field)) {
            return "¥" + value;
        }
        if ("risk_level".equals(field)) {
            return "R" + value;
        }
        return value.toString();
    }
}
