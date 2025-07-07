package com.ai.service.bi;

import cn.hutool.core.util.ObjUtil;
import com.ai.service.ChineseEmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@Log4j2
public class SchemaVectorService {

    private final ChineseEmbeddingService chineseEmbeddingService;
    private MilvusServiceClient milvusClient;
    private final JdbcTemplate jdbcTemplate;
    @Value("${milvus.host}")
    private String host;
    @Value("${milvus.port}")
    private int port;

    // 向量维度 (根据实际使用的嵌入模型确定)
    private static final int VECTOR_DIMENSION = 768;

    // 集合名称
    private static final String COLLECTION_NAME = "table_schemas";

    public SchemaVectorService(JdbcTemplate jdbcTemplate,
                               ChineseEmbeddingService chineseEmbeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.chineseEmbeddingService = chineseEmbeddingService;
    }

    public void init() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        milvusClient = new MilvusServiceClient(connectParam);

        // 先删除，再新增
        milvusClient.dropCollection(DropCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());

        createCollectionIfNotExists();

        // 加载集合到内存
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();
        milvusClient.loadCollection(loadParam);

        vectorizeAndStoreSchemas();
    }

    /**
     * 创建Milvus集合（如果不存在）
     */
    private void createCollectionIfNotExists() {
        // 检查集合是否存在
        if (milvusClient.hasCollection(io.milvus.param.collection.HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build()).getData()) {
            return;
        }

        // 定义字段
        List<FieldType> fields = new ArrayList<>();
        fields.add(FieldType.newBuilder()
                .withName("table_name")
                .withDataType(DataType.VarChar)
                .withMaxLength(50)
                .withPrimaryKey(true)
                .build());
        fields.add(FieldType.newBuilder()
                .withName("schema_text")
                .withDataType(DataType.VarChar)
                .withMaxLength(2000)
                .build());
        fields.add(FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIMENSION)
                .build());

        // 创建集合
        milvusClient.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldTypes(fields)
                .build());

        // 创建索引
        milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":768}")
                .build());
    }

    /**
     * 生成表结构描述文本
     */
    private String generateTableDescription(String tableName, List<Map<String, Object>> columns, Map<String, Object> sampleRow) {
        StringBuilder description = new StringBuilder();
        description.append("表名：").append(tableName).append("\n");
        description.append("字段描述：\n");

        for (Map<String, Object> column : columns) {
            String colName = (String) column.get("COLUMN_NAME");
            String colType = (String) column.get("DATA_TYPE");
            String remarks = (String) column.get("REMARKS");

            description.append("- ")
                    .append(colName)
                    .append(": ")
                    .append(remarks != null ? remarks : colName)
                    .append(" (")
                    .append(colType);

            // 添加类型长度信息（如果有）
            if (column.get("COLUMN_SIZE") != null) {
                description.append("(").append(column.get("COLUMN_SIZE")).append(")");
            }
            description.append(")\n");
        }

        // 拼接示例数据
        /*if (sampleRow != null && !sampleRow.isEmpty()) {
            description.append("\n### 示例数据：\n");
            description.append("| 字段 | 值 |\n");
            description.append("|------|------|\n");
            for (Map.Entry<String, Object> entry : sampleRow.entrySet()) {
                description.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n");
            }
        }*/

        // 拼接示例数据（Excel 表格格式）
        if (ObjUtil.isNotEmpty(sampleRow)) {
            description.append("\n示例数据：\n");

            // 字段名列
            description.append("字段名：\t");
            for (Map.Entry<String, Object> entry : sampleRow.entrySet()) {
                description.append(entry.getKey()).append("\t");
            }
            description.append("\n");

            // 字段值行
            description.append("字段值：\t");
            for (Map.Entry<String, Object> entry : sampleRow.entrySet()) {
                description.append(String.valueOf(entry.getValue())).append("\t");
            }
            description.append("\n");
        }

        return description.toString();
    }

    /**
     * 向量化并存储所有表结构
     */
    public void vectorizeAndStoreSchemas() {
        // 获取所有表名
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class);

        // 存储每个表的向量
        for (String tableName : tableNames) {
            // 获取表结构信息
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE, REMARKS " +
                            "FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_NAME = ?",
                    tableName);
            Map<String, Object> sampleRow = new HashMap<>();

            log.info("Table Name: {}", tableName);
            try {
                sampleRow = jdbcTemplate.queryForMap(
                        "SELECT * FROM " + tableName + " LIMIT 1");
            }catch (Exception e){
                log.warn("Failed to vectorize table: {}, ex msg is :{}", tableName, e);
            }

            // 生成描述文本
            String schemaText = generateTableDescription(tableName, columns, sampleRow);

            // 获取向量
            List<Float> vector = chineseEmbeddingService.getEmbedding(schemaText);

            // 构建插入参数
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("table_name",
                    Collections.singletonList(tableName)));
            fields.add(new InsertParam.Field("schema_text",
                    Collections.singletonList(schemaText)));
            fields.add(new InsertParam.Field("vector",
                    Collections.singletonList(vector)));

            // 插入Milvus
            milvusClient.insert(InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFields(fields)
                    .build());
        }
    }
}
