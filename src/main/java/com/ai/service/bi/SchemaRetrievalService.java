package com.ai.service.bi;

import com.ai.config.MilvusClientConfig;
import com.ai.service.ChineseEmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.naming.directory.SearchResult;
import java.util.Collections;
import java.util.List;

@Service
public class SchemaRetrievalService {

    @Autowired
    private MilvusClientConfig milvusClientConfig;
    private MilvusServiceClient milvusClient;
    private final ChineseEmbeddingService embeddingService;

    private static final String COLLECTION_NAME = "table_schemas";

    public SchemaRetrievalService(ChineseEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 检索与用户问题相关的表结构
     */
    public String getRelevantSchemas(String userQuery) {
        milvusClient = milvusClientConfig.getMilvusServiceClient();
        // 向量化用户问题
        List<Float> queryVector = embeddingService.getEmbedding(userQuery);

        // 构建搜索参数
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.L2)
                .withTopK(4) // 返回最相关的3张表
                .withVectors(Collections.singletonList(queryVector))
                .withVectorFieldName("vector")
                .withOutFields(Collections.singletonList("schema_text"))
                .build();

        // 执行搜索
        R<SearchResults> searchResult = milvusClient.search(searchParam);

        // 处理结果
        if (searchResult.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("向量搜索失败: " + searchResult.getMessage());
        }

        SearchResultsWrapper results = new SearchResultsWrapper(
                milvusClient.search(searchParam).getData().getResults());
        StringBuilder schemas = new StringBuilder();
        List<QueryResultsWrapper.RowRecord> scoreMap = results.getRowRecords(0);

        // 拼接相关表结构
        for (QueryResultsWrapper.RowRecord rowRecord : scoreMap) {
            rowRecord.get("schema_text");
            schemas.append(rowRecord.get("schema_text")).append("\n\n");
        }

        return schemas.toString();
    }
}
