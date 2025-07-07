package com.ai.service;

import cn.hutool.core.util.ObjUtil;
import com.ai.config.SessionManager;
import com.ai.entity.ResearchReport;
import com.ai.entity.UserAssets;
import com.ai.entity.UserProfile;
import com.ai.entity.UserSession;
import com.ai.mapper.AssetInfoMapper;
import com.ai.service.miluvs.MilvusForResearchReportService;
import com.ai.service.prompt.PromptEngineService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class MarketAnalysisService {

    @Autowired
    private PromptEngineService promptEngineService;

    private static final String SYSTEM_PROMPT_TEMPLATE =
            "你是一名专业理财顾问，请根据以下信息提供市场分析：\n%s%s%s" +  // 动态插入画像/持仓/历史
                    "### 相关市场知识（根据用户持仓所属行业）\n%s\n" +  // 知识库内容
                    "### 用户当前问题\n%s\n" +  // 用户输入
                    "### 分析要求\n" +
                    "1. 结合用户画像和当前市场状况进行分析\n" +
                    "2. 给出具体投资建议\n" +
                    "3. 不要包含任何风险提示和免责声明，系统会单独添加\"\n" +
                    "4. 使用中文回复，保持专业且易懂\n" +
                    "5. 展示格式对用户友好清晰，字数 150字左右\n" +
                    "6. 若无明确主题，请提供多元化市场概览\n" +
                    "7. 不要输出任何无关内容，如：字数\n" +
                    "8. 禁止使用任何图表代码（如Mermaid），所有建议均用自然语言描述\n" +
                    "9. 投资路径建议使用以下清晰格式：\n" +
                    "   - 1: [描述]\n" +
                    "   - 2: [描述]\n" +
                    "   - ...\n" +
                    "10. 资产配置建议使用以下格式：\n" +
                    "   • [资产类别]: [比例] - [理由]" +
                    "11. 最后提供总结。\n";

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private AssetInfoMapper assetInfoMapper;
    @Autowired
    private MilvusForResearchReportService milvusForResearchReportService;
    @Autowired
    private DeepSeekClient aiClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    public Flux<?> generateMarketAnalysis(String sessionId, String userId, String userQuery) throws Exception{
        UserSession session = sessionManager.getOrCreateSession(sessionId, userId);
        UserProfile userProfile = session.getUserProfile();
        UserAssets assetInfo = assetInfoMapper.selectOne(new QueryWrapper<UserAssets>().eq("user_id", userId));

        // 动态构建用户画像部分
        String profileSection = buildProfileSection(userProfile);

        // 动态构建持仓部分（假设UserProfile有getHoldings方法）
        String holdingsSection = buildHoldingsSection(userProfile);

        // 获取历史对话
        String recentHistory = buildHistorySection(session, userId);

        // 检索相关知识(根据用户持仓所属行业分析市场)
        List<ResearchReport> knowledge = milvusForResearchReportService.searchSimilarReports("用户持仓行业：" + assetInfo.getTopIndustries(), 2);
        String knowledgeText = formatKnowledge(knowledge);

        // 构建系统提示词
        String prompt = String.format(SYSTEM_PROMPT_TEMPLATE,
                profileSection,
                holdingsSection,
                recentHistory,
                knowledgeText,
                userQuery);

        promptEngineService.generatePrompt(userProfile, assetInfo);
        log.info("Market analysis prompt: {}", prompt);
        return aiClient.chatFluxCompletion(prompt);
    }

    private String buildProfileSection(UserProfile profile) {
        if (ObjUtil.isEmpty(profile)) {
            return "";
        }
        return "### 用户画像\n" +
                "风险偏好: " + safeGet(profile.getRiskPreference()) + "\n" +
                "投资目标: " + safeGet(profile.getInvestmentGoal()) + "\n" +
                "投资期限: " + safeGet(profile.getExpectInvestTime(), "年") + "\n" +
                "风险承受等级: " + safeGet(profile.getRiskTolerance()) + "\n\n";
    }

    private String buildHoldingsSection(UserProfile profile) throws Exception{
        if (ObjUtil.isEmpty(profile.getHoldings())) {
            return "";
        }
        return "### 用户持仓\n" + objectMapper.writeValueAsString(profile.getHoldings()) + "\n\n";
    }

    private String buildHistorySection(UserSession session, String userId) {
        String history = session.getRecentHistory(userId);
        if (history == null || history.isEmpty()) {
            return "### 历史对话\n无\n\n";
        }
        return "### 历史对话\n" + history + "\n\n";
    }


    private String safeGet(String value) {
        return value == null ? "未提供" : value;
    }

    private String safeGet(Integer value) {
        return value == null ? "未提供" : String.valueOf(value);
    }

    private String safeGet(Integer value, String unit) {
        return value == null ? "未提供" : value + unit;
    }

    private String formatKnowledge(List<ResearchReport> knowledge) {
        if (knowledge == null || knowledge.isEmpty()) {
            return "当前无相关市场知识";
        }

        StringBuilder sb = new StringBuilder();
        for (ResearchReport item : knowledge) {
            sb.append("▪ ").append(item.getTitle()).append(": ")
                    .append(item.getSummary()).append("\n");
        }
        return sb.toString();
    }
}