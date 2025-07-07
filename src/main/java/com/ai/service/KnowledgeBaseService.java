package com.ai.service;

import com.ai.service.miluvs.MilvusService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 知识库处理服务
@Service
public class KnowledgeBaseService {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private MilvusService milvusService;
    @Autowired
    private ChineseEmbeddingService embeddingService;

    public void processAndStoreKnowledgeBase() throws Exception {
        // 读取知识库文件
        File file = ResourceUtils.getFile("classpath:knowledge/理财知识库.txt");

        // 解析知识库
        List<Map<String, Object>> knowledgeEntries = parseKnowledgeBase(file);
        // 插入Milvus
        milvusService.insertKnowledge(knowledgeEntries);
    }

    private List<Map<String, Object>> parseKnowledgeBase(File file) throws Exception{
        // 实现知识库解析逻辑，将文本内容转换为结构化数据
        List<String> lines = Files.readAllLines(file.toPath());

        // 解析 JSON 内容为 List<Map<String, Object>>
        return convertJsonsToNestedList(lines);
    }

    public static List<Map<String, Object>> convertJsonsToNestedList(List<String> jsonList) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (String json : jsonList) {
            // 将JSON字符串解析为嵌套Map结构
            Map<String, Object> map = mapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {}
            );
            resultList.add(map);
        }
        return resultList;
    }

    public List<Map<String, Object>> searchSimilarKnowledge(String query, int topK) {
        // 获取查询向量
        List<Float> queryVector = embeddingService.getEmbedding(query);

        // 执行相似性搜索
        return milvusService.searchSimilarKnowledge(queryVector, topK);
    }
}
