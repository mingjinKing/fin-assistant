package com.ai.service.intent;

import cn.hutool.json.JSONArray;
import com.ai.config.MilvusClientConfig;
import com.ai.entity.Conversation;
import com.ai.service.ChineseEmbeddingService;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// Milvus 对话服务实现
@Service
public class MilvusConversationService {

    @Autowired
    private MilvusClientConfig milvusClientConfig;

    private ChineseEmbeddingService chineseEmbeddingService;

    private static final String COLLECTION_NAME = "conversation";
    private static final int VECTOR_DIM = 768;

    private MilvusClient  milvusClient;

    @Value("${milvus.collection.conversations:conversations}")
    private String collectionName;

    @PostConstruct
    public void init() {
        milvusClient = milvusClientConfig.getMilvusServiceClient();
        // 先删除，再新增
        milvusClient.dropCollection(DropCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());
        createCollection();

        // 加载集合到内存
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();
        milvusClient.loadCollection(loadParam);
    }

    private void createCollection() {
        if (milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build()).getData()) return;

        // 定义基础字段
        FieldType idField = FieldType.newBuilder()
                .withName("conversation_id")
                .withDataType(DataType.VarChar)
                .withPrimaryKey(true)
                .withMaxLength(100)
                .build();

        FieldType userIdField = FieldType.newBuilder()
                .withName("user_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(20)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(2000)
                .build();

        // 定义元数据字段
        FieldType timestampField = FieldType.newBuilder()
                .withName("timestamp")
                .withDataType(DataType.Int64)
                .withMaxLength(50)
                .build();

        FieldType intentField = FieldType.newBuilder()
                .withName("intent")
                .withDataType(DataType.VarChar)
                .withMaxLength(50)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIM)
                .build();


        // 创建集合
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .addFieldType(idField)
                .addFieldType(userIdField)
                .addFieldType(contentField)
                .addFieldType(timestampField)
                .addFieldType(intentField)
                .addFieldType(vectorField)
                .build();

        milvusClient.createCollection(createParam);

        // 创建索引
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":1024}")
                .build();

        milvusClient.createIndex(indexParam);

    }


    // 保存对话（带向量）
    public void saveConversation(List<Conversation> conversation, List<Float> vector) {
        List<String> conversations = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        List<String> timestamps = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> intents = new ArrayList<>();

        for (Conversation c : conversation) {
            conversations.add(c.getId());
            vectors.add(vector);
            userIds.add(c.getUserId());
            timestamps.add(c.getTimestamp().toString());
            contents.add(c.getContent());
            intents.add(c.getIntent());
        }

        // 构建插入参数
        ArrayList<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("conversation_id", conversations));
        fields.add(new InsertParam.Field("userId", userIds));
        fields.add(new InsertParam.Field("timestamp", timestamps));
        fields.add(new InsertParam.Field("content", contents));
        fields.add(new InsertParam.Field("intent", intents));
        fields.add(new InsertParam.Field("vector", vectors));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        milvusClient.insert(insertParam);
    }

    // 语义搜索最近对话
    public List<Conversation> searchSimilarConversations(String userId, String currentQuery, int limit) {
        // 1. 生成当前问题向量
        List<Float> queryVector = generateVector(currentQuery);

        // 2. 构建搜索请求
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withVectors(Collections.singletonList(queryVector))
                .withTopK(limit)
                .withParams("{\"nprobe\":16}")
                .withExpr("user_id == '" + userId + "'") // 过滤当前用户
                .withOutFields(Arrays.asList("content", "intent", "timestamp"))
                .build();

        // 3. 执行搜索
        SearchResultsWrapper resultsWrapper = new SearchResultsWrapper(
                milvusClient.search(searchParam).getData().getResults());

        // 4. 转换结果
        // 获取分数和记录
        List<SearchResultsWrapper.IDScore> scores = resultsWrapper.getIDScore(0);
        List<QueryResultsWrapper.RowRecord> rowsMap = resultsWrapper.getRowRecords(0);

        // 组合结果
        List<Conversation> scoredResults = new ArrayList<>();
        for (int i = 0; i < rowsMap.size(); i++) {
            QueryResultsWrapper.RowRecord row = rowsMap.get(i);
            Conversation conv = new Conversation(
                    (String) row.get("content"),
                    (String) row.get("intent"),
                    Long.parseLong(row.get("timestamp").toString()),
                    scores.get(i).getScore()
            );
            scoredResults.add(conv);
        }
        return scoredResults;
    }

    private List<Float> generateVector(String text) {
        // 调用DeepSeek嵌入API（简化示例）
        return chineseEmbeddingService.getEmbedding(text);
    }
}
