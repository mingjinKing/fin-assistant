package com.ai.service;

import com.ai.service.miluvs.MilvusService;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class QAService {

    private final ChineseEmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final DeepSeekClient deepSeekClient; // 来自pig-mesh的聊天服务

    public QAService(ChineseEmbeddingService embeddingService,
                     MilvusService milvusService,
                     DeepSeekClient deepSeekClient) {
        this.embeddingService = embeddingService;
        this.milvusService = milvusService;
        this.deepSeekClient = deepSeekClient;
    }

    public Flux<ChatCompletionResponse> answerQuestion(String question) throws Exception{
        // 1. 生成问题嵌入向量
        List<Float> questionEmbedding = embeddingService.getEmbedding(question);

        // 2. 检索相关文档
        List<Map<String, Object>> contexts = milvusService.searchSimilarKnowledge(questionEmbedding, 3);

        String prompt = buildFinancePrompt(contexts, question);

        // 4. 调用DeepSeek聊天API
        return deepSeekClient.chatFluxCompletion(prompt);
    }

    private String buildFinancePrompt(List<Map<String, Object>> contexts, String question) {
        // 1. 构建知识上下文
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("### 专业理财知识库：\n");

        for (Map<String, Object> item : contexts) {
            String text = (String) item.get("text");
            String knowledgeType = (String) item.getOrDefault("knowledge_type", "通用知识");
            String productCategory = (String) item.getOrDefault("product_category", "");
            String riskLevel = (String) item.getOrDefault("risk_level", "");

            // 添加带元数据标记的知识点
            contextBuilder.append(String.format(
                    "[%s] %s",
                    knowledgeType,
                    text
            ));

            // 添加产品类别和风险等级标记
            if (!productCategory.isEmpty()) {
                contextBuilder.append(String.format(" (产品类型: %s", productCategory));
            }
            if (!riskLevel.isEmpty()) {
                contextBuilder.append(String.format(" | 风险等级: %s", riskLevel));
            }

            contextBuilder.append("\n\n");
        }

        // 2. 添加系统指令
        String systemInstruction =
                "### 角色设定：\n" +
                        "你是一名专业理财顾问，必须严格遵循以下规则：\n" +
                        "1. 所有回答必须基于提供的<专业理财知识库>\n" +
                        "2. 涉及产品时必须包含风险提示\n" +
                        "3. 禁止推荐具体金融产品\n" +
                        "4. 涉及计算需展示公式和示例\n" +
                        "5. 结尾必须添加标准免责声明\n\n" +
                        "### 回答格式要求：\n" +
                        "- 专业准确：使用金融术语但解释清晰\n" +
                        "- 风险提示：对提及产品自动添加对应风险\n" +
                        "- 免责声明：固定结尾文本";

        // 3. 添加固定免责声明
        String disclaimer =
                "\n\n### 免责声明：\n" +
                        "理财非存款，产品有风险，投资须谨慎。过往业绩不代表未来表现。" +
                        "本信息仅供参考，不构成任何投资建议。具体产品请以官方说明书为准。";

        // 4. 整合完整Prompt
        return systemInstruction + "\n\n" +
                "### 专业理财知识库：\n" + contextBuilder.toString() + "\n" +
                "### 用户问题：\n" + question + "\n\n" +
                "### 专业回答：" + disclaimer;
    }

    private String buildPrompt(String context, String question) {
        return "你是一个专业的理财助手，请严格根据提供的<相关知识>来回答用户问题。回答需专业、清晰、简洁。\n" +
                "如果知识不足以回答问题，请告知用户无法回答，并建议咨询专业顾问。\n" +
                "务必在回答中包含必要的风险提示。\n" +
                "\n" +
                "<相关知识>\n" + context +
                "\n" +
                "</相关知识>\n" +
                "\n" +
                "用户问题：\n" + question +
                "\n" +
                "请回答：\n";
    }
}