package com.ai.service;

import cn.hutool.core.util.ObjUtil;
import com.ai.entity.*;
import com.ai.exception.ProductNotFoundException;
import com.ai.service.bi.AdvancedSqlTemplateEngine;
import com.ai.service.bi.ChatBIService;
import com.ai.util.CommonUtils;
import com.ai.util.DeepSeekClientUtils;
import com.ai.util.FluxUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 产品解读
@Slf4j
@Service
public class AnalysisAgent {
    @Autowired
    private DeepSeekClient deepSeekClient;
    @Autowired
    private KnowledgeBaseService knowledgeBase;
    @Autowired
    private AdvancedSqlTemplateEngine advancedSqlTemplateEngine;
    @Autowired
    private JdbcTemplate  jdbcTemplate;
    @Autowired
    private ChatBIService chatBIService;
    ObjectMapper objectMapper = new ObjectMapper();

    public Flux<?> handle(String userInput, UserSession session) throws  Exception{
        // 1. 产品识别增强
        String productId = CommonUtils.extractProductId(userInput, session);
        log.info("待分析产品ID: {}", productId);
        if (StringUtils.isBlank(productId)) {
            return Flux.just("请说明您想了解的产品名称或编号？");
        }

        // 2. 获取产品信息
        String otherRequirements = "产品查询不超过五个";
        String prompt = String.format("1. 查询产品（基金、理财）信息：%s \n" +
               // "2. 参考用户风险等级:%s \n" +
                "2. 产品查询不超过五个(暂不考虑用户风险等级) \n" +
                "3. 基金产品主要查询基金表（funds_products），理财产品主要查询理财表（financial_products）"
                ,productId, session.getUserProfile().getRiskTolerance());
        String sql = advancedSqlTemplateEngine.genSQL(prompt);
        List<Map<String, Object>>  prodInfoList =  jdbcTemplate.queryForList(sql);
        /*List<String> prodInfo = chatBIService.naturalLanguageQuery(String.format("用户输入为: %s\n" +
                "查询产品：%s\n", userInput, productId), session.getUserProfile(), objectMapper.writeValueAsString(new ArrayList<ProductInfo>()), otherRequirements);*/
        if(ObjUtil.isNotEmpty(prodInfoList)){
           /* List<ProductInfo> prodInfoList = new ArrayList<>();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            try {
                prodInfoList = objectMapper.readValue(prodInfo.get(0), new TypeReference<List<ProductInfo>>() {});
            }catch (Exception e){
                log.error("产品信息解析失败:{}", e.getMessage());
            }*/

            // 3. 异步获取市场动态和持仓分析
            List<String> prodTypeList = prodInfoList.stream().map(item -> (String)item.get("product_type")).collect(Collectors.toList());
            List<Map<String, Object>> marketInfo = knowledgeBase.searchSimilarKnowledge(prodTypeList.toString(), 5);
            List<Map<String, Object>> portfolioData = session.getPortfolioData();

            // 3. 多维度分析
            return generateReport(prodInfoList, marketInfo, portfolioData, session.getUserProfile());
        }else{
            List<Map<String, Object>> marketInfo = knowledgeBase.searchSimilarKnowledge(userInput, 2);
            List<Map<String, Object>> portfolioData = session.getPortfolioData();

            // 3. 多维度分析
            return generateReport(prodInfoList, marketInfo, portfolioData, session.getUserProfile());
        }
    }

    private Flux<?> generateReport(List<Map<String, Object>> products, List<Map<String, Object>> marketData,
                                   List<Map<String, Object>> portfolio, UserProfile profile) throws  Exception{
        // 构建提示词
        String prompt = buildReportPrompt(products, marketData, portfolio, profile);
        log.info("AnalysisAgent prompt info is:{}",  prompt);
        return deepSeekClient.chatFluxCompletion(prompt);
    }

    private String buildReportPrompt(List<Map<String, Object>> products, List<Map<String, Object>> marketData,
                                     List<Map<String, Object>> portfolio, UserProfile profile) throws Exception {
        // 1. 多产品信息格式化
       /* String productsInfo = products.stream()
                .map(this::formatProductInfo)
                .collect(Collectors.joining("\n"));

        // 2. 风险匹配信息生成
        String riskMatches = products.stream()
                .map(p -> String.format("%s (%s) → %s",
                        p.get("product_name"),
                        p.getRiskLevel(),
                        getRiskStatus(p.getRiskLevel(), profile.getRiskTolerance())))
                .collect(Collectors.joining("\n- "));*/

        return String.format("## 产品深度分析报告生成指令\n" +
                        "**角色**：资深金融分析师\n" +
                        "**任务**：为高净值客户生成多产品投资分析报告\n" +
                        "\n" +
                        "### 分析产品列表\n" +
                        "%s\n\n" +
                        "### 市场动态（最近3个月）\n" +
                        "%s\n\n" +
                        "### 用户画像\n" +
                        "- 风险承受：%s级（1-5级）\n" +
                        "- 资产规模：%s\n" +
                        "- 投资目标：%s\n" +
                        "- 当前持仓：%s\n\n" +
                        "### 分析维度要求\n" +
                        "1. 【综合评估】\n" +
                        "   - 对比产品特性与用户画像、用户持仓匹配度\n" +
                        "2. 【亮点分析】（按产品分列）\n" +
                        "   - 产品用⭐标注1-2个核心优势\n\n" +
                        "3. 【组合建议】\n" +
                        "   - 基于现有持仓提出配置优化方向\n" +
                        "   - 不同市场情景下的调整建议\n\n" +
                        "**输出规范**：\n" +
                        "- 风险警示：❗️符号标注\n" +
                        "- 综合建议尽量给出模糊话术，如增加、减少，禁止给出明确数字\n" +
                        "- 禁止出现：DeepSeek AI 助手之类的话术，角色定位为“智能理财助手”\n" +
                        "- 结束标记：◆◆ 报告结束 ◆◆",
                objectMapper.writeValueAsString(products),
                formatMarketInfo(marketData),
                profile.getRiskTolerance(),
                profile.getHoldings(),
                profile.getInvestmentGoal(),
                formatPortfolioInfo(portfolio));
    }
    private String formatProductInfo(ProductInfo product) {
        return String.format(" - 名称：%s (ID: %s)\n" +
                        " - 类型：%s\n" +
                        " - 风险等级：%s\n" +
                        " - 年收益：%s\n" +
                        " - 起购金额：%s\n" +
                        " - 资产类别：%s",
                product.getProductName(), product.getProductCode(),
                product.getProductType(),
                product.getRiskLevel(),
                product.getAnnualReturn(),
                product.getMinPurchaseAmount(),
                product.getAssetClass());
    }

    private String formatMarketInfo(List<Map<String, Object>> marketData) {
        if (marketData == null || marketData.isEmpty()) {
            return "> ❗️ 市场数据暂时不可用";
        }

        return marketData.stream()
                .limit(3)
                .map(data -> String.format(" - **%s**：%s \n" +
                                "      \uD83D\uDCC5 %s | \uD83D\uDCCC %s",
                        data.get("title"),
                        data.get("summary"),
                        data.get("date"),
                        data.get("source")))
                .collect(Collectors.joining("\n"));
    }

    private String formatPortfolioInfo(List<Map<String, Object>> portfolio) throws Exception{
        return String.format("持仓情况：%s",
                objectMapper.writeValueAsString(portfolio));
    }

    private String getRiskStatus(String productRisk, int userTolerance) {
        if(StringUtils.isEmpty(productRisk)) {
            productRisk = "R0";
        }
        if (userTolerance > Integer.parseInt(productRisk.substring(1))) return "✅ 安全";
        if (userTolerance == Integer.parseInt(productRisk.substring(1))) return "⚠️ 临界";
        return "❗️ 超限";
    }

    private String getLiquidityStatus(String productLiquidity, String userRequirement) {
        if ("高".equals(productLiquidity) && !"每日".equals(userRequirement)) return "✅ 充足";
        if ("中".equals(productLiquidity) && "每周".equals(userRequirement)) return "⚠️ 适中";
        return "❗️ 不足";
    }

}
