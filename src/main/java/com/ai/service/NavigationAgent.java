package com.ai.service;

import cn.hutool.json.JSONUtil;
import com.ai.entity.*;
import com.ai.service.bi.ChatBIService;
import com.ai.service.miluvs.SQLFingerPrintService;
import com.ai.util.FluxUtils;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionModel;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionRequest;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import com.ai.service.MarketAnalysisService;

import java.util.List;
import java.util.Map;

// 探索期 agent
@Service
@Log4j2
public class NavigationAgent {


    @Autowired
    private DeepSeekClient deepSeekClient;
    @Autowired
    private KnowledgeBaseService knowledgeBase;
    @Autowired
    private ChatBIService chatBIService;
    @Autowired
    private AnalysisAgent analysisAgent;
    @Autowired
    private MarketAnalysisService marketAnalysisService;
    @Autowired
    private SQLFingerPrintService sqlFingerPrintService;
    private ObjectMapper mapper = new ObjectMapper();

    public Flux<?>  handle(String userInput, UserSession session) throws Exception{
        // 1. 意图识别
        Intent intent = detectIntent(userInput);
        log.info("意图识别结果：{}", intent);

        // 2. 根据意图处理
        switch (intent){
            case PRODUCT_QUERY:
                return analysisAgent.handle(userInput, session);
                //return handleProductQuery(userInput, session);
            case HOLDING_ANALYSIS:
                return handleHoldingAnalysis(userInput, session);
            case FINANCIAL_QA:
                return handleKnowledgeQuery(userInput);
            case MARKET_ANALYSIS:
                return handleMarketAnalysis(userInput, session);
            case GENERAL_GUIDE:
                return provideGeneralGuide(userInput);
            default:
                return FluxUtils.string2Flux("抱歉！服务异常");
        }


    }

    private Flux<?> handleMarketAnalysis(String userInput, UserSession session) throws Exception{
        return marketAnalysisService.generateMarketAnalysis(session.getSessionId(), session.getUserProfile().getUserId(), userInput);
    }

    private Intent detectIntent(String input) {
        String prompt = String.format("用户输入：%s\n" +
                "请分类意图：\n" +
                "1. 产品查询（包含产品名称或产品代码或产品关键字，产品相关的问题）\n" +
                "2. 持仓分析（包含'持仓'、'持有'等关键词）\n" + // 强化持仓识别
                "3. 金融知识问答\n" +
                "4. 金融市场咨询（包含'行情'、'市场’等关键词）\n" +
                "5. 意图不明，需要追问\n" +
                "只输出数字", input);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                // 根据渠道模型名称动态修改这个参数
                .model(ChatCompletionModel.DEEPSEEK_CHAT)
                .addUserMessage(prompt).build();

        ChatCompletionResponse modelResult= deepSeekClient.chatCompletion(request).execute();
        String intentCategory = modelResult.choices().get(0).message().content();;
        log.info("大模型返回意图类别：{}", intentCategory);

        try {
            int code = Integer.parseInt(intentCategory.trim());
            if (code >= 1 && code <= Intent.values().length) {
                return Intent.values()[code-1];
            }
        } catch (NumberFormatException e) {
            log.info("意图识别异常: {}", intentCategory, e);
        }
        return Intent.GENERAL_GUIDE; // 默认返回一般引导
    }

    // 新增持仓分析方法
    private Flux<?> handleHoldingAnalysis(String query, UserSession session) throws JsonProcessingException {
        // 1. 通过chatBIService获取持仓数据
       /* List<String> portfolio = chatBIService.naturalLanguageQuery(
                "获取用户持仓详情，包括产品名称、持仓金额、占比、收益率和风险等级",
                session.getUserProfile(), mapper.writeValueAsString(new PortfolioData()),
                ""
        );*/
        String queryDb = String.format("当前用户: %s 持有的所有投资产品(基金、理财)情况，包括产品标识、产品类型、持有金额、购买时间、当前收益表现，以及对应的产品名称", session.getUserProfile().getUserId());
        List<Map<String, Object>> portfolio = sqlFingerPrintService.generateSQLAndQueryDB(queryDb);

        log.info("portfolio is:{}", portfolio);
        // 2. 解析持仓数据
        //PortfolioData response = mapper.readValue(portfolio.get(0), new TypeReference<PortfolioData>() {});

        UserProfile userProfile = session.getUserProfile();

        // 3. 构建分析提示词
        String prompt = buildHoldingAnalysisPrompt(query, portfolio, userProfile);
        log.info("持仓分析提示词: {}", prompt);

        // 3. 调用大模型生成分析报告
        return deepSeekClient.chatFluxCompletion(prompt);
    }


    // 构建持仓分析提示词
    private String buildHoldingAnalysisPrompt(String query, List<Map<String, Object>> portfolio, UserProfile userProfile) throws JsonProcessingException{
        return String.format(
                "你是一位资深理财分析师，请根据用户持仓数据和问题，生成专业分析报告。要求：\n" +
                        "1. 包含风险评估（1-5级）\n" +
                        "2. 指出持仓结构问题\n" +
                        "3. 给出优化建议\n" +
                        "4. 语言简洁专业，不超过200字\n\n" +
                        "### 用户问题：\n%s\n\n" +
                        "### 持仓数据：\n%s" +
                        "### 用户画像：\n%s" +
                        "### 市场参考：\n" +
                        "当前市场波动率：%.2f%%\n" +
                        "行业平均收益：%.2f%%\n\n" +
                        "请开始分析：",
                query,
                JSONUtil.toJsonStr(portfolio), JSONUtil.toJsonStr(userProfile), new Float("2.1"),new Float("4")
        );
    }

    // 格式化产品分布信息
    private String formatProductDistribution(List<PortfolioData.Product> products) {
        StringBuilder sb = new StringBuilder();
        for (PortfolioData.Product product : products) {
            sb.append(String.format("- %s：%.2f%% (%.2f元)\n",
                    product.getName(),
                    product.getPercentage(),
                    product.getValue()));
        }
        return sb.toString();
    }

    private Flux<ChatCompletionResponse> handleKnowledgeQuery(String question) {
        // 从知识库检索
        List<Map<String, Object>> knowledge = knowledgeBase.searchSimilarKnowledge(question, 3);
        // 生成回答
        StringBuilder context = new StringBuilder();
        knowledge.forEach(item -> context.append(item.get("text")).append("\n"));
        String prompt = String.format("根据知识库内容回答问题：\n" +
                "            知识：%s\n" +
                "            问题：%s\n" +
                "            要求：\n：" +
                "            1. 生成内容禁止提到无关内容：如 DeepSeek AI 助手，字数"
                ,context, question);

        return deepSeekClient.chatFluxCompletion(prompt);
    }

    private Flux<?> provideGeneralGuide(String userInput) {
        // 构建追问提示词
        String prompt = String.format("用户说: \"%s\"\n" +
                "作为理财导航助手，你需要追问用户的具体需求。请根据用户输入生成一个简洁的追问问题（不超过15字），" +
                "帮助明确用户是想查询产品、了解金融知识还是其他服务。\n" +
                "追问示例：\"您想了解哪类产品？\"\"具体想查询什么金融知识？\"\n" +
                "直接输出追问内容（不要加引号或任何前缀）", userInput);

        // 调用大模型生成追问
        return deepSeekClient.chatFluxCompletion(prompt);

    }

    enum Intent { PRODUCT_QUERY, HOLDING_ANALYSIS, FINANCIAL_QA, MARKET_ANALYSIS, GENERAL_GUIDE}
}
