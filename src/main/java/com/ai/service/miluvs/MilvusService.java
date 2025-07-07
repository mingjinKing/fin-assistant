package com.ai.service.miluvs;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.ai.service.ChineseEmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
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
import java.util.*;

@Service
public class MilvusService {

    @Autowired
    private ChineseEmbeddingService embeddingService;

    @Value("${milvus.host}")
    private String host;
    @Value("${milvus.port}")
    private int port;

    // MilvusService.java 新增字段定义
    private static final String COLLECTION_NAME = "knowledge_base";
    private static final int VECTOR_DIM = 768; // BERT 嵌入维度
    private MilvusServiceClient client;

    @PostConstruct
    public void init() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        client = new MilvusServiceClient(connectParam);
        // 先删除，再新增
        client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());
        createCollection();

        // 加载集合到内存
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();
        client.loadCollection(loadParam);
    }

    // 修改 createCollection 方法以支持元数据字段
    private void createCollection() {
        if (client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build()).getData()) return;

        // 定义基础字段
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType textField = FieldType.newBuilder()
                .withName("text")
                .withDataType(DataType.VarChar)
                .withMaxLength(2000)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIM)
                .build();

        // 定义元数据字段
        FieldType knowledgeTypeField = FieldType.newBuilder()
                .withName("knowledge_type")
                .withDataType(DataType.VarChar)
                .withMaxLength(50)
                .build();

        FieldType productCategoryField = FieldType.newBuilder()
                .withName("product_category")
                .withDataType(DataType.VarChar)
                .withMaxLength(50)
                .build();

        FieldType riskLevelField = FieldType.newBuilder()
                .withName("risk_level")
                .withDataType(DataType.VarChar)
                .withMaxLength(10)
                .build();

        FieldType keyTermsField = FieldType.newBuilder()
                .withName("key_terms")
                .withDataType(DataType.Array)
                .withElementType(DataType.VarChar)
                .withMaxCapacity(100)
                .withMaxLength(100)
                .build();


        // 创建集合
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .addFieldType(idField)
                .addFieldType(textField)
                .addFieldType(vectorField)
                .addFieldType(knowledgeTypeField)
                .addFieldType(productCategoryField)
                .addFieldType(riskLevelField)
                .addFieldType(keyTermsField)
                .build();

        client.createCollection(createParam);

        // 创建索引
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":1024}")
                .build();

        client.createIndex(indexParam);
    }

    // 新增带元数据的插入方法
    public void insertKnowledge(List<Map<String, Object>> knowledgeEntries) {
        List<String> texts = new ArrayList<>();
        List<List<Float>> vectors = new ArrayList<>();
        List<String> knowledgeTypes = new ArrayList<>();
        List<String> productCategories = new ArrayList<>();
        List<String> riskLevels = new ArrayList<>();
        List<JSONArray> keyTermsJson = new ArrayList<>();

        for (Map<String, Object> entry : knowledgeEntries) {
            texts.add((String) entry.get("text"));

            // 生成向量
            List<Float> vector = embeddingService.getEmbedding((String) entry.get("text"));
            vectors.add(vector);

            // 处理元数据
            Map<String, Object> metadata = (Map<String, Object>) entry.get("metadata");
            knowledgeTypes.add((String) metadata.get("knowledge_type"));

            productCategories.add(metadata.containsKey("product_category") ?
                    (String) metadata.get("product_category") : "");

            riskLevels.add(metadata.containsKey("risk_level") ?
                    (String) metadata.get("risk_level") : "");

            // 转换关键词为JSON字符串
            List<String> keyTerms = (List<String>) metadata.get("key_terms");
            keyTermsJson.add(keyTerms != null ? JSONUtil.parseArray(keyTerms) : new JSONArray());

        }

        // 构建插入参数
        ArrayList<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("text", texts));
        fields.add(new InsertParam.Field("embedding", vectors));
        fields.add(new InsertParam.Field("knowledge_type", knowledgeTypes));
        fields.add(new InsertParam.Field("product_category", productCategories));
        fields.add(new InsertParam.Field("risk_level", riskLevels));
        fields.add(new InsertParam.Field("key_terms", keyTermsJson));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        client.insert(insertParam);

        // 刷新数据
        client.flush(FlushParam.newBuilder()
                .addCollectionName(COLLECTION_NAME)
                .build());
    }


   /* public void insertDocuments(List<String> contents, List<List<Float>> vectors) {
        ArrayList<InsertParam.Field> fields = new ArrayList<InsertParam.Field>();
        fields.add(new InsertParam.Field("text", contents));
        fields.add(new InsertParam.Field("embedding", vectors));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        client.insert(insertParam);

        // 刷新数据使可搜索
        client.flush(FlushParam.newBuilder()
                .addCollectionName(COLLECTION_NAME)
                .build());
    }*/

    // 修改 searchSimilarDocuments 方法以返回完整信息
    public List<Map<String, Object>> searchSimilarKnowledge(List<Float> queryVector, int topK) {
        String SEARCH_PARAM = "{\"nprobe\":10}";
        List<Map<String, Object>> resultList = new ArrayList<>();
        List<String> outputFields = Arrays.asList(
                "text", "knowledge_type", "product_category", "risk_level","key_terms"
        );

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.L2)
                .withOutFields(outputFields)
                .withTopK(topK)
                .withVectors(Arrays.asList(queryVector))
                .withVectorFieldName("embedding")
                .withParams(SEARCH_PARAM)
                .build();

        SearchResultsWrapper results = new SearchResultsWrapper(
                client.search(searchParam).getData().getResults());

        List<QueryResultsWrapper.RowRecord> scoreMap = results.getRowRecords(0);
        for (QueryResultsWrapper.RowRecord rowRecord : scoreMap) {
            Map<String, Object> result = new HashMap<>();

            result.put("text", rowRecord.get("text"));
            result.put("knowledge_type", rowRecord.get("knowledge_type"));
            result.put("product_category", rowRecord.get("product_category"));
            result.put("risk_level", rowRecord.get("risk_level"));

            // 解析JSON格式的关键词
            try {
                JSONArray keyTermsArray = JSONUtil.parseArray(rowRecord.get("key_terms").toString());
                result.put("key_terms", keyTermsArray.toList(String.class));
            } catch (Exception e) {
                result.put("key_terms", new ArrayList<String>());
            }

            result.put("distance", rowRecord.get("distance"));

            resultList.add(result);
        }

        return resultList;
    }

    public List<String> searchFundsByIndustry(String industry, int limit) {
        List<String> results = new ArrayList<>();
        try{
            // 1. 将行业关键词转换为向量
            float[] industryVector = generateIndustryVector(industry);

            // 2. 构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName("")
                    .withVectors(Collections.singletonList(industryVector))
                    .withTopK(limit)
                    .build();

            // 3. 执行搜索
            SearchResultsWrapper resultsWrapper = new SearchResultsWrapper(
                    client.search(searchParam).getData().getResults());

            // 4. 提取基金代码
            List<QueryResultsWrapper.RowRecord> scoreMap = resultsWrapper.getRowRecords(0);
            for (QueryResultsWrapper.RowRecord rowRecord : scoreMap) {
                results.add(String.valueOf(rowRecord.get("fund_code")));
            }


           return results;
        } catch (Exception e) {
            // 降级到空结果，上层会处理
            return Collections.emptyList();
        }
    }

    // 行业关键词向量化（简化版）
    private float[] generateIndustryVector(String industry) {
        // 实际应使用DeepSeek嵌入API
        // 这里使用简单哈希作为示例
        int dimension = 128;
        float[] vector = new float[dimension];
        int hash = industry.hashCode();

        for (int i = 0; i < dimension; i++) {
            vector[i] = (hash % 100) / 100.0f;
            hash = hash * 31 + i;
        }
        return vector;
    }

}
