package com.ai.service;

import com.ai.entity.*;
import com.ai.service.bi.ChatBIService;
import com.ai.service.miluvs.MilvusForResearchReportService;
import com.ai.util.RiskUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

// 产品推荐 agent
@Slf4j
@Service
public class RecommendationAgent {


    @Autowired
    private MilvusForResearchReportService milvusService;
    @Autowired
    private ChineseEmbeddingService chineseEmbeddingService;
    @Autowired
    private ChatBIService chatBIService;
    @Autowired
    private DeepSeekClient deepSeekClient;
    // 增加持仓服务注入
    @Autowired
    private PortfolioService portfolioService;
    ObjectMapper objectMapper = new ObjectMapper();

    public Flux<?> handle(String userQuery, UserSession session) throws Exception{
        // 1. 获取用户画像和持仓
        UserProfile profile = session.getUserProfile();
        log.info("用户画像: {}", profile);
        List<Map<String, Object>> portfolio = session.getPortfolioData(); // 获取结构化持仓
        log.info("持仓数据: {}", portfolio);
        //PortfolioData portfolio = new PortfolioData().build();
        // 2. 检索相关研报
        List<ResearchReport> reports = retrieveResearchReports(userQuery);
        log.info("研报数据: {}", reports.size());

        // 3. 优化提示词构建
        String prompt = buildAnalysisPrompt(userQuery, profile,reports, portfolio);
        log.info("分析提示词: {}", prompt);

        return deepSeekClient.chatFluxCompletion(prompt);
    }

    private String buildAnalysisPrompt(String userQuery, UserProfile profile,
                                       List<ResearchReport> reports,
                                       List<Map<String, Object>> portfolio) throws  Exception{
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名专业理财顾问，请基于以下信息提供投资建议：\n");

        // 用户画像（融合增强版格式）
        prompt.append("### 用户画像：\n")
                .append("风险等级：").append(profile.getRiskTolerance())
                .append("级(").append(RiskUtils.formatRiskLevel(Double.parseDouble(profile.getRiskTolerance().substring(1,2)))).append(")\n")
                .append("偏好类型：").append(String.join(",", profile.getPreferredFundTypes())).append("\n");
                //.append("总资产：").append(String.format("%.2f元", portfolio.getTotalAssets())).append("\n\n");

        // 当前持仓（使用增强版格式化）
        prompt.append("### 当前持仓：\n")
                .append(objectMapper.writeValueAsString(portfolio)).append("\n\n");

        // 市场环境（从增强版迁移）
        prompt.append("### 市场环境：\n")
                .append(String.format("市场波动率：%.2f，行业平均回报：%.2f%%",
                        2.3,
                        0.021 * 100))
                .append("\n\n");

        // 研报摘要（保留原版逻辑）
        prompt.append("### 相关研报摘要（").append(reports.size()).append("篇）：\n");
        reports.forEach(r -> prompt.append("- [")
                .append(r.getTitle()).append("] ")
                .append(r.getSummary())
                .append("\n"));

        // 用户查询
        prompt.append("\n### 用户查询：").append(userQuery).append("\n\n");

        // 融合版输出要求（合并两者核心要素）
        prompt.append("### 回答要求：\n")
                .append("1. 您的情况：用户持仓、风险偏好等\n")
                .append("2. 配置策略：针对持仓不足/过度暴露的资产提出调整方案\n")
                .append("3. 逻辑说明：结合用户风险偏好、市场环境和研报分析\n")
                .append("4. 操作建议：禁止提供具体的基金代码和比例，对用户展示友好\n")
                .append("5. 总结：不超过100字或3句话的核心结论\n")
                .append("6. 严格把握智能理财助手角色的定位与称呼，禁止出现DeepSeek AI 助手之类的无关信息，严格按照 1、2、3、4、5的顺序阐述\n")
                .append("7. 严格遵守金融合规要求：\n")
                .append("   - 避免使用绝对收益承诺\n")
                .append("   - 标注\"历史业绩不代表未来表现\"\n")
                .append("   - 禁止推荐非许可金融产品");

        return prompt.toString();
    }

    private List<ResearchReport> retrieveResearchReports(String query) {
        // 使用Milvus向量检索
        return milvusService.searchSimilarReports(query, 5);
    }

    // 格式化持仓数据
    private String formatPortfolio(List<PortfolioData.Product> products) {
        StringBuilder sb = new StringBuilder();
        for (PortfolioData.Product p : products) {
            sb.append(String.format(
                    "- %s(%s)：占比%.2f%%，年化%.2f%%，风险%s级\n",
                    p.getName(), p.getId(), p.getPercentage(),
                    p.getAnnualizedReturn()*100, p.getRiskLevel()
            ));
        }
        return sb.toString();
    }
}
