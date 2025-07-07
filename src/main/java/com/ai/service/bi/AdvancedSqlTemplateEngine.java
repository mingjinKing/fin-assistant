package com.ai.service.bi;

import cn.hutool.core.util.ObjectUtil;
import com.ai.config.MilvusClientConfig;
import com.ai.config.SessionManager;
import com.ai.config.SystemContextManager;
import com.ai.entity.SqlQueryModel;
import com.ai.entity.SqlTemplate;
import com.ai.entity.SqlTemplateEntity;
import com.ai.service.ChineseEmbeddingService;
import com.ai.service.DynamicSqlService;
import com.ai.service.IntentService;
import com.ai.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import com.hubspot.jinjava.lib.fn.ELFunctionDefinition;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.log4j.Log4j2;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.internal.Function;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

// 增强的SQL模板引擎（支持复杂查询）
@Component
@Log4j2
public class AdvancedSqlTemplateEngine {
    private Jinjava jinjava = new Jinjava();
    @Autowired
    private SchemaRetrievalService schemaRetrievalService;
    @Autowired
    private IntentService intentService;
    private static final float SCORE_THRESHOLD = 0.90f;
    private ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    public static final String COLLECTION_NAME = "sql_fingerprints";
    private MilvusClient milvusClient;

    @Autowired
    private MilvusClientConfig milvusClientConfig;
    @Autowired
    private ChineseEmbeddingService chineseEmbeddingService;
    @Autowired
    private DynamicSqlService dynamicSqlService;
    @Autowired
    private SystemContextManager systemContextManager;

    @PostConstruct
    public void init() {
        milvusClient = milvusClientConfig.getMilvusServiceClient();
    }

    // 使用构造函数初始化
    public AdvancedSqlTemplateEngine() {
        jinjava = new Jinjava();
        registerCustomExtensions();
    }

    // 单独提取注册方法
    private void registerCustomExtensions() {
        // 注册过滤器
        jinjava.registerFilter(new ConditionsRenderer());
        jinjava.registerFilter(new ValueEscapeFilter());

        // 注册函数 - 关键修复：使用静态方法
        jinjava.registerFunction(new ELFunctionDefinition(
                "",
                "render_conditions",
                ConditionsRenderer.class,
                "renderConditionGroupStatic", // 改为静态方法
                SqlQueryModel.ConditionGroup.class
        ));
    }

    public String render(SqlQueryModel model) {
        // 确保所有字段不为 null
        if (model.getConditions() == null) {
            model.setConditions(new SqlQueryModel.ConditionGroup());
        }

        log.info("render model: {}", model);

        // 关键修复：对tables进行排序，确保主表（joinType为null）排在第一位
        List<SqlQueryModel.Table> sortedTables = new ArrayList<>(model.getTables());
        sortedTables.sort((t1, t2) -> {
            // 主表（joinType为null）应该排在前面
            if (t1.getJoinType() == null && t2.getJoinType() != null) return -1;
            if (t1.getJoinType() != null && t2.getJoinType() == null) return 1;
            return 0; // 其他情况保持原有顺序
        });
        model.setTables(sortedTables);

        // 使用修复后的SQL模板
        String template = "SELECT\n" +
                "          {% for field in fields %}\n" +
                "            {{ field.expr }}{% if field.alias %} AS \"{{ field.alias }}\"{% endif %}{% if not loop.last %},{% endif %}\n" +
                "          {% endfor %}\n" +
                "        FROM\n" +
                "          {% for table in tables %}\n" +
                "            {% if table.joinType == 'MAIN' or (loop.first and table.joinType == null) %}\n" +
                "              {{ table.name }}{% if table.alias %} {{ table.alias }}{% endif %}\n" +
                "            {% elif table.joinType %}\n" +
                "              {{ table.joinType }} {{ table.name }}{% if table.alias %} {{ table.alias }}{% endif %}{% if table.onClause %} ON {{ table.onClause }}{% endif %}\n" +
                "            {% else %}\n" +
                "              , {{ table.name }}{% if table.alias %} {{ table.alias }}{% endif %}\n" +
                "            {% endif %}\n" +
                "          {% endfor %}\n" +
                "        {% if conditions %}WHERE {{ render_conditions(conditions) }}{% endif %}\n" +
                "        {% if groupBy %}GROUP BY {{ groupBy|join(', ') }}{% endif %}\n" +
                "        {% if having %}HAVING \n" +
                "            {% for cond in having %}\n" +
                "                {{ cond.field }} {{ cond.operator }} {{ cond.value|escape_value(cond.valueType) }}\n" +
                "                {% if not loop.last %} AND {% endif %}\n" +
                "            {% endfor %}\n" +
                "        {% endif %}\n" +
                "        {% if orderBy %}ORDER BY {% for order in orderBy %}{{ order.field }} {{ order.direction }}{% if not loop.last %}, {% endif %}{% endfor %}{% endif %}\n" +
                "        {% if limit %}LIMIT {{ limit }}{% endif %}";

        Map<String, Object> context = new HashMap<>();
        context.put("tables", model.getTables());
        context.put("fields", model.getFields());
        context.put("conditions", model.getConditions());
        context.put("groupBy", model.getGroupBy());
        context.put("having", model.getHaving());
        context.put("orderBy", model.getOrderBy());
        context.put("limit", model.getLimit());
        context.put("render_conditions", new ConditionsRenderer());

        return jinjava.render(template, context);
    }

    // 条件渲染器（保持原有逻辑）
    public static class ConditionsRenderer implements Filter{
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public String getName() {
            return "render_conditions";
        }

        @Override
        public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
            if (var instanceof SqlQueryModel.ConditionGroup) {
                return renderConditionGroup((SqlQueryModel.ConditionGroup) var);
            }
            return var;
        }

        public String render(Object var) {
            if (var instanceof SqlQueryModel.ConditionGroup) {
                return renderConditionGroup((SqlQueryModel.ConditionGroup) var);
            }
            return var != null ? var.toString() : "";
        }

        private String renderConditionGroup(SqlQueryModel.ConditionGroup group) {
            if (group == null || group.getConditions() == null || group.getConditions().isEmpty()) {
                return "1=1"; // 返回有效的默认条件
            }

            StringBuilder sb = new StringBuilder("(");
            List<Object> validConditions = group.getConditions().stream()
                    .filter(Objects::nonNull) // 过滤掉 null 条件
                    .collect(Collectors.toList());

            for (int i = 0; i < validConditions.size(); i++) {
                Object condition = validConditions.get(i);

                if (condition instanceof SqlQueryModel.Condition) {
                    sb.append(renderCondition((SqlQueryModel.Condition) condition));
                } else if (condition instanceof SqlQueryModel.ConditionGroup) {
                    sb.append(renderConditionGroup((SqlQueryModel.ConditionGroup) condition));
                } else if (condition instanceof Map) {
                    try {
                        // 添加空值检查
                        Map<?, ?> conditionMap = (Map<?, ?>) condition;
                        if (conditionMap != null && !conditionMap.isEmpty()) {
                            SqlQueryModel.Condition cond = objectMapper.convertValue(condition, SqlQueryModel.Condition.class);
                            sb.append(renderCondition(cond));
                        } else {
                            sb.append("1=1"); // 安全回退
                        }
                    } catch (IllegalArgumentException e) {
                        sb.append("1=1"); // 安全回退
                    }
                }

                if (i < validConditions.size() - 1) {
                    sb.append(" ").append(group.getType()).append(" ");
                }
            }
            sb.append(")");
            return sb.toString();
        }

        private String renderCondition(SqlQueryModel.Condition cond) {
            if (cond == null) {
                return "1=1"; // 处理空条件
            }
            return String.format("%s %s %s",
                    cond.getField(),
                    cond.getOperator(),
                    escapeValue(cond.getValue(), cond.getValueType()));
        }

        private String escapeValue(Object value, String valueType) {
            // 使用之前完善的转义方法实现
            return ValueEscapeUtil.escapeValue(value, valueType);
        }

        // 添加静态渲染方法
        public static String renderConditionGroupStatic(SqlQueryModel.ConditionGroup group) {
            return new ConditionsRenderer().renderConditionGroup(group);
        }
    }
    // 自定义值转义过滤器
    public static class ValueEscapeFilter implements Filter {
        @Override
        public String filter(Object var, JinjavaInterpreter interpreter, String... args) {
            String valueType = args.length > 0 ? args[0] : "STRING";
            if (var instanceof SqlQueryModel.Condition) {
                SqlQueryModel.Condition cond = (SqlQueryModel.Condition) var;
                return escapeValue(cond.getValue(), valueType);
            }
            return escapeValue(var, valueType);
        }

        @Override
        public String getName() {
            return "escape_value";
        }

        private String escapeValue(Object value, String valueType) {
            if (value == null) {
                return "NULL";
            }

            // 处理数组和列表类型（如IN操作符的值）
            if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                return collection.stream()
                        .map(item -> escapeSingleValue(item, valueType))
                        .collect(Collectors.joining(", ", "(", ")"));
            } else if (value.getClass().isArray()) {
                return Arrays.stream((Object[]) value)
                        .map(item -> escapeSingleValue(item, valueType))
                        .collect(Collectors.joining(", ", "(", ")"));
            }

            return escapeSingleValue(value, valueType);
        }

        private String escapeSingleValue(Object value, String valueType) {
            if (value == null) {
                return "NULL";
            }

            String strValue = value.toString();

            // 根据值类型进行不同的转义处理
            switch (valueType != null ? valueType.toUpperCase() : "STRING") {
                case "STRING":
                    // 转义单引号并包裹在单引号中
                    return "'" + strValue.replace("'", "''") + "'";

                case "DATE":
                    // 日期类型使用DATE关键字
                    return "DATE '" + strValue + "'";

                case "DATETIME":
                    // 日期时间类型
                    return "TIMESTAMP '" + strValue + "'";

                case "BOOLEAN":
                case "BOOL":
                    // 布尔值不转义
                    return strValue;

                case "NUMBER":
                case "INT":
                case "DECIMAL":
                case "FLOAT":
                    // 数字类型不转义
                    return strValue;

                case "LIST":
                    // 列表类型特殊处理
                    if (value instanceof Collection) {
                        Collection<?> collection = (Collection<?>) value;
                        return collection.stream()
                                .map(item -> escapeSingleValue(item, "STRING"))
                                .collect(Collectors.joining(", ", "(", ")"));
                    }
                    return strValue;

                case "NULL":
                    // 显式NULL值
                    return "NULL";

                case "COLUMN":
                    // 列名不转义，直接使用
                    return strValue;

                case "FUNCTION":
                    // 函数调用不转义
                    return strValue;

                default:
                    // 默认按字符串处理
                    return "'" + strValue.replace("'", "''") + "'";
            }
    }
    }

    public SqlQueryModel generateSqlModel(String userQuery, String formatExample) {
        try {
            // 1. 检索相关表结构
            String relevantSchemas = schemaRetrievalService.getRelevantSchemas(userQuery);
            String fieldsDescription = (formatExample != null && !formatExample.isEmpty())
                    ? "6. 数据库表字段别名严格参考数据格式示例: " + formatExample
                    : "";

            String prompt = String.format("用户问题: %s\n" +
                            "            请将以上查询转换为JSON格式，包含以下字段:\n" +
                            "            - table: 表名\n" +
                            "            - fields: 查询字段列表\n" +  // 动态添加描述
                            "            - conditions: 条件列表(包含field/operator/value)\n" +
                            "            - orderBy: 排序字段及方向\n" +
                            "            - limit: 结果限制数\n" +
                            "            表结构相关信息为: %s\n" +
                            "            要求: \n" +
                            "            1. 只输出 JSON 内容 \n" +
                            "            2. 严格按照示例JSON格式输出:\n %s \n" +
                            "            3. 注意：主表joinType为空; 联表查询注意语法，如 left join成对出现\n" +
                            "            4. 严格校验数据库表的字段，若条件不明确，偏向 like 语法查询产品 \n" +
                            "            5. 设置查询条件时，请重点参见表结构信息相关中数据示例\n" +
                            "            6. 注意去重\n" +
                            "            7. 语法满足H2数据库 \n %s"
                    , userQuery, relevantSchemas, objectMapper.writeValueAsString(new SqlQueryModel()), fieldsDescription);
            log.info("SQL Query prompt is: {}", prompt);
            long start = System.currentTimeMillis();
            String json = DeepSeekClientUtils.getDeepSeekContentWithChatModel(prompt);
            log.info("deepseek response time: {} ms", System.currentTimeMillis() - start);
            String realContent = CommonUtils.extractSqlFromMarkdown(json, "json");
            log.info("deepseek response content: {}", realContent);
            return objectMapper.readValue(realContent, SqlQueryModel.class);
        }catch (Exception e){
            log.error("Failed to generate sql model", e);
        }
        return null;
    }

    public String genSQL(String userQuery) {
        // 优先从<语义指纹，sql 模板>映射中获取
        long startTime = System.currentTimeMillis();
       /* String sqlFromTemplate = getSQLFromTemplate(userQuery);
        log.info("SQL from template: {}, excute time is :{}", sqlFromTemplate, System.currentTimeMillis() - startTime);
        if(ObjectUtil.isNotEmpty(sqlFromTemplate)) return sqlFromTemplate;*/

        // Step1: 大模型生成JSON结构
        SqlQueryModel model = generateSqlModel(userQuery, null);
        if (model == null) {
            log.error("Failed to generate SQL model from user query: {}", userQuery);
            return null;
        }

        int retryCount = 0;
        int maxRetries = 3; // 最大重试次数
        String sql = null;

        while (retryCount <= maxRetries) {
            try {
                // Step2: 模板渲染SQL
                sql = render(model);
                log.info("Rendered SQL: {}", sql);

                // Step3: 验证SQL安全性
                String formatSQL = SqlTemplateExtractor.validate(sql);

                // 建立<语义指纹，sql 模板>映射关系
                // 使用：
                executor.submit(() -> {
                    String intent = intentService.detectIntent(userQuery);
                    SqlTemplateEntity sqlTemplate = new SqlTemplateEntity();
                    sqlTemplate.setQuestion(userQuery);
                    sqlTemplate.setSqlTemplate(formatSQL);
                    sqlTemplate.setIntentTag(intent);
                    try {
                        addNewMapping(sqlTemplate);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                log.info("Valid SQL generated after {} retries: {}", retryCount, formatSQL);
                return formatSQL;
            } catch (Exception e) {
                retryCount++;
                log.warn("SQL validation failed on attempt {}: {}", retryCount, e.getMessage());
                if (retryCount > maxRetries) {
                    log.error("Max retries reached. Failed to generate valid SQL.");
                    break;
                }
            }
        }

        return null;
    }



    public String getSQLFromTemplate(String userQuery){

        // 1. 意图识别
        String intent = intentService.detectIntent(userQuery);

        // 2. 检索
        List<SqlTemplateEntity> candidates = findSimilarTemplate(userQuery, intent, 3);
        log.info("userQuery is :{}, intent is:{}", userQuery, intent);

        // 重排与过滤
        // 4. 重排序和阈值过滤
        Optional<SqlTemplateEntity> bestMatch = candidates.stream()
                .peek(template -> {
                    // 意图匹配加分 (相同意图+10%)
                    if (template.getIntentTag().equals(intent)) {
                        template.setScore(template.getScore() * 1.1f);
                    }
                })
                .filter(template -> template.getScore() > SCORE_THRESHOLD)
                .max(Comparator.comparing(SqlTemplateEntity::getScore));

        String sqlTemplate = bestMatch.map(SqlTemplateEntity::getSqlTemplate)
                .orElse(null);

        if (sqlTemplate != null) {
            Map<String,String> systemContextMap = systemContextManager.getContextParams();
            // 大模型根据用户输入、sql 模板、系统上下文确认真正的 sql
            String prompt = String.format(
                    "你是一个SQL生成助手。请根据以下三部分信息生成最终可执行的SQL语句：%n" +
                            "1. **用户输入**: \"%s\"%n" +
                            "2. **SQL模板**: \"%s\"%n" +
                            "3. **系统上下文**:%n%s%n" +
                            "### 任务要求 ###%n" +
                            "- 将SQL模板中的变量替换为系统上下文中的具体值（如${var} -> value）%n" +
                            "- 根据用户输入调整查询条件（如补充WHERE子句）%n" +
                            "- 严格确保输出内容只包含最终SQL语句，无任何额外文本%n" +
                            "### 输出示例 ###%n" +
                            "SELECT * FROM table WHERE id = 123",
                    userQuery,
                    sqlTemplate,
                    formatSystemContext(systemContextMap)  // 格式化上下文为键值对
            );
            log.info("getSQLFromTemplate prompt: {}" , prompt);
            String generatedSql = DeepSeekClientUtils.getDeepSeekContentWithChatModel( prompt);

            return CommonUtils.extractSqlFromMarkdown(generatedSql, "sql");
        }
        return null;
    }

    // 辅助方法：将HashMap格式化为易读的键值对字符串
    private static String formatSystemContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return "    (无上下文信息)";
        }
        return context.entrySet().stream()
                .map(entry -> String.format("    - %s: \"%s\"", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    public void addNewMappingBatch(List<SqlTemplateEntity> templates){
        MilvusClient client = milvusClientConfig.getMilvusServiceClient();
        templates.forEach((sqlTemplate) -> {
            try {
                log.info("正在添加新映射：{}", sqlTemplate.getSqlTemplate());
                addNewMapping(sqlTemplate);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        // 刷新索引
        client.flush(FlushParam.newBuilder()
                .addCollectionName(COLLECTION_NAME)
                .build());
    }

    // 添加新映射
    public void addNewMapping(SqlTemplateEntity sqlTemplateEntity) throws InterruptedException {
        log.info("正在添加新映射, 问题为：{}，模板为：{}", sqlTemplateEntity.getQuestion(), sqlTemplateEntity.getSqlTemplate());
        MilvusClient milvusClient = milvusClientConfig.getMilvusServiceClient();
        String sql = sqlTemplateEntity.getSqlTemplate();
        String nlq = sqlTemplateEntity.getQuestion();
        String intentTag = sqlTemplateEntity.getIntentTag();
        SqlTemplate template =  SqlTemplateExtractor.extractTemplate(preprocessSqlTemplate(sql));
        List<Float> embedding = chineseEmbeddingService.getEmbedding(nlq);

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("semantic_vector",
                Collections.singletonList(embedding)));
        fields.add(new InsertParam.Field("sql_template",
                Collections.singletonList(template.getTemplate())));
        fields.add(new InsertParam.Field("intent_tag",
                Collections.singletonList(intentTag)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build();

        milvusClient.insert(insertParam);

        sleep(100);
    }

    public String preprocessSqlTemplate(String sqlTemplate) {
        // 替换字符串类型的占位符为问号
        sqlTemplate = sqlTemplate.replaceAll("\\{[^}]+\\}", "?");

        // 替换列名占位符（如 {return_type}_return）为固定列名
        sqlTemplate = sqlTemplate.replaceAll("\\{return_type\\}_return", "monthly_return");

        return sqlTemplate;
    }

    // 查找匹配的SQL模板
    public List<SqlTemplateEntity> findSimilarTemplate(String nlq, String intent, int topK) {
        List<SqlTemplateEntity> result = new ArrayList<>();
        // 生成语义指纹
        List<Float> embedding = chineseEmbeddingService.getEmbedding(nlq);
        List<List<Float>> searchVectors = Collections.singletonList(embedding);

        String expr = "intent_tag == \"" + intent + "\"";

        List<String> outputFields = Arrays.asList("fingerprint_id", "sql_template", "intent_tag");

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withMetricType(MetricType.IP)  // 内积相似度
                .withOutFields(outputFields)
                .withTopK(topK)
                .withVectors(searchVectors)
                .withVectorFieldName("semantic_vector")
                .withExpr(expr)  // 关键意图过滤
                .withParams("{\"nprobe\":32}")
                .build();

        // 执行搜索
        // 3. 执行搜索
        R<SearchResults> response = milvusClient.search(searchParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("搜索失败: " + response.getMessage());
        }

        // 4. 解析结果
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<QueryResultsWrapper.RowRecord> scoreMap = wrapper.getRowRecords(0);
        for (QueryResultsWrapper.RowRecord rowRecord : scoreMap) {
            SqlTemplateEntity item = new SqlTemplateEntity();
            item.setSqlTemplate((String)rowRecord.get("sql_template"));
            item.setScore((Float) rowRecord.get("distance"));
            item.setIntentTag((String)rowRecord.get("intent_tag"));
            result.add(item);
        }
        return result;
    }



}

