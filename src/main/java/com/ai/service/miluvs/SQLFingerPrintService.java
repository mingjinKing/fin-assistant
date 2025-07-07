package com.ai.service.miluvs;

import com.ai.config.MilvusClientConfig;
import com.ai.config.SessionManager;
import com.ai.entity.SqlTemplate;
import com.ai.entity.SqlTemplateEntity;
import com.ai.service.ChineseEmbeddingService;
import com.ai.service.DynamicSqlService;
import com.ai.service.IntentService;
import com.ai.service.bi.AdvancedSqlTemplateEngine;
import com.ai.service.bi.ChatBIService;
import com.ai.util.DeepSeekClientUtils;
import com.ai.util.SqlTemplateExtractor;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

import static java.lang.Thread.sleep;

@Slf4j
@Service
public class SQLFingerPrintService {

    @Autowired
    private MilvusClientConfig milvusClientConfig;
    @Autowired
    private ChineseEmbeddingService chineseEmbeddingService;
    @Autowired
    private ChatBIService chatBIService;
    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private AdvancedSqlTemplateEngine sqlTemplateEngine;
    @Autowired
    private DynamicSqlService dynamicSqlService;

    private MilvusClient client;

    public static final String COLLECTION_NAME = "sql_fingerprints";
    private static final float SCORE_THRESHOLD = 0.78f;

    public static final int DIM = 768;
    @Autowired
    private IntentService intentService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        client = milvusClientConfig.getMilvusServiceClient();
        // 先删除，再新增
        client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());

        initMilvusCollection();
        // 加载集合到内存
        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build();
        client.loadCollection(loadParam);
    }

    public void initMilvusCollection() {
        // 创建集合（如果不存在）
        if (!client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build()).getData()) {

            FieldType field1 = FieldType.newBuilder()
                    .withName("fingerprint_id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build();

            FieldType field2 = FieldType.newBuilder()
                    .withName("semantic_vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(DIM)
                    .build();

            // 定义元数据字段
            FieldType intentTagField = FieldType.newBuilder()
                    .withName("intent_tag")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(50)
                    .build();

            FieldType field3 = FieldType.newBuilder()
                    .withName("sql_template")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(2048)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withDescription("SQL语义指纹映射")
                    .addFieldType(field1)
                    .addFieldType(field2)
                    .addFieldType(field3)
                    .addFieldType(intentTagField)
                    .build();

            client.createCollection(createParam);

            // 创建索引
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFieldName("semantic_vector")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.IP) // 内积相似度
                    .withExtraParam("{\"nlist\":1024}")
                    .build();

            client.createIndex(indexParam);
        }
    }


    public String generateSQL(String nlq) {
        return sqlTemplateEngine.genSQL(nlq);
    }

    public List<Map<String, Object>> generateSQLAndQueryDB(String nlq) {
        List<String> result = new ArrayList<>();
        // 1. 生成 sql
        String realSql = sqlTemplateEngine.genSQL(nlq);

        //return dynamicSqlService.executeTemplateSql(realSql, null);

        // 5. 执行SQL查询
        return jdbcTemplate.queryForList(realSql);
        //log.info("chatBi queryResults size is:{}", queryResults.size());

        /*if(queryResults.isEmpty()) return result;

        queryResults.forEach(map -> {
            String queryRow =new String((byte[])map.get("RESULT")) ;
            result.add(queryRow);
        });
        return result;*/
    }

}
