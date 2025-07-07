package com.ai.service.miluvs;

import com.ai.config.MilvusClientConfig;
import com.ai.entity.ResearchReport;
import com.ai.service.ChineseEmbeddingService;
import com.ai.service.data.BatchReportParser;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class MilvusForResearchReportService {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;
    @Autowired
    private BatchReportParser batchReportParser;
    @Autowired
    private ChineseEmbeddingService chineseEmbeddingService;

    @Autowired
    private MilvusClientConfig  milvusClientConfig;

    private MilvusServiceClient milvusClient;

    private static final String COLLECTION_NAME = "research_reports";

    // 向量维度 (需根据实际嵌入模型调整)
    private static final int VECTOR_DIMENSION = 768;

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

    /**
     * 创建 Milvus 集合
     */
    public void createCollection() {

            // 2. 定义字段 Schema
            FieldType reportIdField = FieldType.newBuilder()
                    .withName("reportId")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64) // VARCHAR 最大长度
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();

            FieldType titleField = FieldType.newBuilder()
                    .withName("title")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(512)
                    .build();

            FieldType industryField = FieldType.newBuilder()
                    .withName("industry")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(128)
                    .build();

            FieldType confidenceField = FieldType.newBuilder()
                    .withName("confidenceScore")
                    .withDataType(DataType.Float)
                    .build();

            FieldType filePathField = FieldType.newBuilder()
                    .withName("filePath")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(1024)
                    .build();

        FieldType summary = FieldType.newBuilder()
                .withName("summary")
                .withDataType(DataType.VarChar)
                .withMaxLength(5000)
                .build();

            // 向量字段 (存储文本嵌入向量)
            FieldType vectorField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.FloatVector)
                    .withDimension(VECTOR_DIMENSION)
                    .build();

            // 3. 创建集合参数
            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withDescription("AI research reports storage")
                    .addFieldType(reportIdField)
                    .addFieldType(titleField)
                    .addFieldType(industryField)
                    .addFieldType(confidenceField)
                    .addFieldType(filePathField)
                    .addFieldType(summary)
                    .addFieldType(vectorField) // 必须包含向量字段
                    .build();

            // 4. 执行创建
            R<RpcStatus> response = milvusClient.createCollection(createParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建集合失败: " + response.getMessage());
            }

            // 5. 创建向量索引 (IVF_FLAT + L2 距离)
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFieldName("content")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.L2)
                    .withExtraParam("{\"nlist\":1024}") // 聚类单元数
                    .build();

            R<RpcStatus> indexResponse = milvusClient.createIndex(indexParam);
            if (indexResponse.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建索引失败: " + indexResponse.getMessage());
            }

    }


    public void ParseAndSaveReports() {
        // 报告存储目录（相对项目根目录）
        String reportsDir = "src/main/resources/knowledge/report";
        log.info("reportsDir is: {}", reportsDir);
        try {
            List<ResearchReport> reports = batchReportParser.parseReportsDirectory(reportsDir);
            for (ResearchReport report : reports){
                saveReport(report);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Finished parsing and saving reports.");
    }

    public void saveReport(ResearchReport report) {
        List<Float> content = chineseEmbeddingService.getEmbedding(report.getContent());
        // 构建插入数据
        List<InsertParam.Field> fields =  Arrays.asList(
                new InsertParam.Field("reportId", Collections.singletonList(report.getReportId())),
                new InsertParam.Field("title", Collections.singletonList(report.getTitle())),
                new InsertParam.Field("industry", Collections.singletonList(report.getIndustry())),
                new InsertParam.Field("content", Collections.singletonList(content)),
                new InsertParam.Field("filePath", Collections.singletonList(report.getFilePath())),
                new InsertParam.Field("summary", Collections.singletonList(report.getSummary())),
                new InsertParam.Field("confidenceScore", Collections.singletonList(report.getConfidenceScore()))
        );

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        R<MutationResult> response = milvusClient.insert(insertParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus 存储失败: " + response.getMessage());
        }
    }

    public List<ResearchReport> searchSimilarReports(String query, int topK) {
        // 1. 将查询文本转换为向量
        List<Float> queryVector = chineseEmbeddingService.getEmbedding(query);
        List<List<Float>> searchVectors = Collections.singletonList(queryVector);

        // 2. 构建搜索参数
        String searchParamIVF = "{\"nprobe\":32}"; // IVF索引的搜索参数
        List<String> outputFields = Arrays.asList(
                "reportId", "title", "industry", "confidenceScore", "filePath", "summary", "content"
        );

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withVectorFieldName("content")
                .withVectors(searchVectors)
                .withTopK(topK)
                .withMetricType(MetricType.L2)
                .withParams(searchParamIVF)
                .withOutFields(outputFields)
                .build();

        // 3. 执行搜索
        R<SearchResults> response = milvusClient.search(searchParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("搜索失败: " + response.getMessage());
        }

        // 4. 解析结果
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<ResearchReport> results = new ArrayList<>();
        List<QueryResultsWrapper.RowRecord> scoreMap = wrapper.getRowRecords(0);
        for (QueryResultsWrapper.RowRecord rowRecord : scoreMap) {
            ResearchReport report = new ResearchReport();
            report.setReportId((String) rowRecord.get("reportId"));
            report.setTitle((String) rowRecord.get("title"));
            report.setIndustry((String) rowRecord.get("industry"));
            report.setConfidenceScore((Float) rowRecord.get("confidenceScore"));
            report.setFilePath((String) rowRecord.get("filePath"));
            //report.setContent((String) rowRecord.get("content"));
            report.setSummary((String) rowRecord.get("summary"));
            results.add(report);
        }

        return results;
    }
}
