package com.ai.util;

import com.ai.entity.ParamType;
import com.ai.entity.SqlTemplate;
import com.ai.entity.TemplateParam;
import lombok.extern.log4j.Log4j2;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import org.springframework.util.DigestUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class SqlTemplateExtractor {

    private static int paramCounter = 0;
    private static final List<TemplateParam> params = new ArrayList<>();
    private static final StringBuilder templateBuilder = new StringBuilder();

    // 字段名计数器，用于处理相同字段多次出现的情况
    private static final Map<String, Integer> fieldNameCounter = new HashMap<>();

    // 正则匹配占位符
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)_(\\d+)\\}");

    // 主提取方法
    public static SqlTemplate extractTemplate(String sql) {
        // 重置状态
        paramCounter = 0;
        params.clear();
        templateBuilder.setLength(0);
        fieldNameCounter.clear(); // 重置字段计数器

        // 解析SQL
        Statement statement = null;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            log.error("SQL {}, 解析错误：{}" , sql, e.getMessage());
        }

        // 自定义Select解析器
        ExpressionDeParser expressionDeParser = new ExpressionDeParser() {
            @Override
            public void visit(Column column) {
                // 列名保留原样
                templateBuilder.append(column.getFullyQualifiedName());
            }

            @Override
            public void visit(StringValue value) {
                // 对于未关联字段的字符串值，保留原有逻辑
                String placeholder = addParam(ParamType.STRING, value.getValue());
                templateBuilder.append(placeholder);
            }

            @Override
            public void visit(LongValue value) {
                // 对于未关联字段的长整型值，保留原有逻辑
                String placeholder = addParam(ParamType.NUMBER, value.getValue());
                templateBuilder.append(placeholder);
            }

            @Override
            public void visit(DoubleValue value) {
                // 对于未关联字段的双精度值，保留原有逻辑
                String placeholder = addParam(ParamType.NUMBER, value.getValue());
                templateBuilder.append(placeholder);
            }

            @Override
            public void visit(DateValue value) {
                // 对于未关联字段的日期值，保留原有逻辑
                String placeholder = addParam(ParamType.DATE, value.getValue());
                templateBuilder.append(placeholder);
            }

            @Override
            public void visit(TimestampValue value) {
                // 对于未关联字段的时间戳值，保留原有逻辑
                String placeholder = addParam(ParamType.DATETIME, value.getValue());
                templateBuilder.append(placeholder);
            }

            @Override
            public void visit(ExpressionList expressionList) {
                // 处理表达式列表
                templateBuilder.append("(");
                for (Iterator<Expression> iter = expressionList.getExpressions().iterator(); iter.hasNext();) {
                    Expression expression = iter.next();
                    expression.accept(this);
                    if (iter.hasNext()) {
                        templateBuilder.append(", ");
                    }
                }
                templateBuilder.append(")");
            }

            @Override
            public void visit(SubSelect subSelect) {
                // 处理子查询
                String placeholder = addParam(ParamType.SUBQUERY, subSelect.toString());
                templateBuilder.append("(").append(placeholder).append(")");
            }

            @Override
            public void visit(AndExpression andExpression) {
                // 处理AND表达式
                visitBinaryExpression(andExpression, " AND ");
            }

            @Override
            public void visit(OrExpression orExpression) {
                // 处理OR表达式
                visitBinaryExpression(orExpression, " OR ");
            }

            @Override
            public void visit(EqualsTo equalsTo) {
                // 处理等于表达式，关联字段名
                if (tryHandleFieldValueExpression(equalsTo, " = ")) {
                    return;
                }
                visitBinaryExpression(equalsTo, " = ");
            }

            @Override
            public void visit(GreaterThan greaterThan) {
                // 处理大于表达式，关联字段名
                if (tryHandleFieldValueExpression(greaterThan, " > ")) {
                    return;
                }
                visitBinaryExpression(greaterThan, " > ");
            }

            @Override
            public void visit(GreaterThanEquals greaterThanEquals) {
                // 处理大于等于表达式，关联字段名
                if (tryHandleFieldValueExpression(greaterThanEquals, " >= ")) {
                    return;
                }
                visitBinaryExpression(greaterThanEquals, " >= ");
            }

            @Override
            public void visit(MinorThan minorThan) {
                // 处理小于表达式，关联字段名
                if (tryHandleFieldValueExpression(minorThan, " < ")) {
                    return;
                }
                visitBinaryExpression(minorThan, " < ");
            }

            @Override
            public void visit(MinorThanEquals minorThanEquals) {
                // 处理小于等于表达式，关联字段名
                if (tryHandleFieldValueExpression(minorThanEquals, " <= ")) {
                    return;
                }
                visitBinaryExpression(minorThanEquals, " <= ");
            }

            @Override
            public void visit(NotEqualsTo notEqualsTo) {
                // 处理不等于表达式，关联字段名
                if (tryHandleFieldValueExpression(notEqualsTo, " <> ")) {
                    return;
                }
                visitBinaryExpression(notEqualsTo, " <> ");
            }

            @Override
            public void visit(InExpression inExpression) {
                // 处理IN表达式，关联字段名
                if (inExpression.getLeftExpression() instanceof Column &&
                        inExpression.getRightItemsList() instanceof ExpressionList) {

                    Column column = (Column) inExpression.getLeftExpression();
                    List<Expression> expressions = ((ExpressionList) inExpression.getRightItemsList()).getExpressions();

                    templateBuilder.append(column.getFullyQualifiedName());
                    if (inExpression.isNot()) {
                        templateBuilder.append(" NOT");
                    }
                    templateBuilder.append(" IN (");

                    for (int i = 0; i < expressions.size(); i++) {
                        Expression exp = expressions.get(i);
                        if (isSimpleValue(exp)) {
                            String placeholder = addParamWithField(column, exp);
                            templateBuilder.append(placeholder);
                            if (i < expressions.size() - 1) {
                                templateBuilder.append(", ");
                            }
                        } else {
                            exp.accept(this);
                        }
                    }
                    templateBuilder.append(")");
                    return;
                }
                super.visit(inExpression);
            }

            @Override
            public void visit(Between between) {
                // 处理BETWEEN表达式，关联字段名
                if (between.getLeftExpression() instanceof Column) {
                    Column column = (Column) between.getLeftExpression();

                    templateBuilder.append(column.getFullyQualifiedName());
                    if (between.isNot()) {
                        templateBuilder.append(" NOT");
                    }
                    templateBuilder.append(" BETWEEN ");

                    Expression startExpr = between.getBetweenExpressionStart();
                    Expression endExpr = between.getBetweenExpressionEnd();

                    if (isSimpleValue(startExpr)) {
                        String startPlaceholder = addParamWithField(column, startExpr);
                        templateBuilder.append(startPlaceholder);
                    } else {
                        startExpr.accept(this);
                    }

                    templateBuilder.append(" AND ");

                    if (isSimpleValue(endExpr)) {
                        String endPlaceholder = addParamWithField(column, endExpr);
                        templateBuilder.append(endPlaceholder);
                    } else {
                        endExpr.accept(this);
                    }
                    return;
                }
                super.visit(between);
            }

            @Override
            public void visit(Function function) {
                // 处理函数
                String functionName = function.getName().toLowerCase();

                // 特殊处理日期函数
                if (functionName.equals("date_add") || functionName.equals("date_sub")) {
                    handleDateFunction(function);
                    return;
                }

                templateBuilder.append(function.getName()).append("(");
                if (function.isDistinct()) {
                    templateBuilder.append("DISTINCT ");
                }
                if (function.getParameters() != null) {
                    function.getParameters().accept(this);
                }
                templateBuilder.append(")");
            }

            private void handleDateFunction(Function function) {
                // 处理日期函数
                templateBuilder.append(function.getName()).append("(");
                function.getParameters().getExpressions().get(0).accept(this);
                templateBuilder.append(", INTERVAL ");

                // 提取间隔值
                Expression intervalExpr = function.getParameters().getExpressions().get(1);
                if (isSimpleValue(intervalExpr)) {
                    String placeholder = addParam(ParamType.NUMBER, intervalExpr.toString());
                    templateBuilder.append(placeholder);
                } else {
                    intervalExpr.accept(this);
                }

                // 处理时间单位
                String unitValue = "DAY"; // 默认值
                if (function.getNamedParameters() != null) {
                    for (String name : function.getNamedParameters().getNames()) {
                        if ("unit".equalsIgnoreCase(name)) {
                            unitValue = name;
                            break;
                        }
                    }
                }

                templateBuilder.append(" ").append(unitValue);
                templateBuilder.append(")");
            }

            // 尝试处理字段-值表达式
            private boolean tryHandleFieldValueExpression(BinaryExpression expr, String operator) {
                // 左边是字段，右边是值
                if (expr.getLeftExpression() instanceof Column &&
                        isSimpleValue(expr.getRightExpression())) {

                    Column column = (Column) expr.getLeftExpression();
                    Expression value = expr.getRightExpression();

                    String placeholder = addParamWithField(column, value);
                    templateBuilder.append(column.getFullyQualifiedName())
                            .append(operator)
                            .append(placeholder);
                    return true;
                }
                // 右边是字段，左边是值
                else if (expr.getRightExpression() instanceof Column &&
                        isSimpleValue(expr.getLeftExpression())) {

                    Column column = (Column) expr.getRightExpression();
                    Expression value = expr.getLeftExpression();

                    String placeholder = addParamWithField(column, value);
                    templateBuilder.append(placeholder)
                            .append(operator)
                            .append(column.getFullyQualifiedName());
                    return true;
                }
                return false;
            }

            // 判断是否为简单值类型
            private boolean isSimpleValue(Expression expr) {
                return expr instanceof StringValue ||
                        expr instanceof LongValue ||
                        expr instanceof DoubleValue ||
                        expr instanceof DateValue ||
                        expr instanceof TimestampValue ||
                        expr instanceof TimeValue ||
                        expr instanceof HexValue;
            }

            // 添加带字段名的参数
            private String addParamWithField(Column column, Expression expr) {
                // 获取字段基础名（不带表名前缀）
                String baseName = column.getColumnName();

                // 处理字段名重复情况
                int count = fieldNameCounter.getOrDefault(baseName, 0);
                String paramName = count > 0 ? baseName + "_" + count : baseName;
                fieldNameCounter.put(baseName, count + 1);

                // 根据值类型确定参数类型
                ParamType type = getValueType(expr);

                // 获取实际值
                Object value = extractValue(expr);

                // 生成占位符
                String placeholder = "{" + paramName + "}";
                params.add(new TemplateParam(placeholder, type, value, templateBuilder.length()));
                return placeholder;
            }

            // 从表达式提取实际值
            private Object extractValue(Expression expr) {
                if (expr instanceof StringValue) {
                    return ((StringValue) expr).getValue();
                } else if (expr instanceof LongValue) {
                    return ((LongValue) expr).getValue();
                } else if (expr instanceof DoubleValue) {
                    return ((DoubleValue) expr).getValue();
                } else if (expr instanceof DateValue) {
                    return ((DateValue) expr).getValue();
                } else if (expr instanceof TimestampValue) {
                    return ((TimestampValue) expr).getValue();
                } else if (expr instanceof TimeValue) {
                    return ((TimeValue) expr).getValue();
                } else if (expr instanceof HexValue) {
                    return ((HexValue) expr).getValue();
                }
                return expr.toString();
            }

            // 根据值类型获取参数类型
            private ParamType getValueType(Expression expr) {
                if (expr instanceof StringValue) return ParamType.STRING;
                if (expr instanceof LongValue) return ParamType.NUMBER;
                if (expr instanceof DoubleValue) return ParamType.NUMBER;
                if (expr instanceof DateValue) return ParamType.DATE;
                if (expr instanceof TimestampValue) return ParamType.DATETIME;
                if (expr instanceof TimeValue) return ParamType.TIME;
                if (expr instanceof HexValue) return ParamType.HEX;
                return ParamType.UNKNOWN;
            }

            protected void visitBinaryExpression(BinaryExpression expr, String operator) {
                expr.getLeftExpression().accept(this);
                templateBuilder.append(operator);
                expr.getRightExpression().accept(this);
            }
        };

        // 自定义Select解析器
        SelectDeParser selectDeParser = new SelectDeParser(expressionDeParser, templateBuilder) {
            @Override
            public void visit(PlainSelect plainSelect) {
                // 处理LIMIT
                if (plainSelect.getLimit() != null) {
                    handleLimit(plainSelect.getLimit());
                }

                // 处理OFFSET
                if (plainSelect.getOffset() != null) {
                    handleOffset(plainSelect.getOffset());
                }

                super.visit(plainSelect);
            }

            private void handleLimit(Limit limit) {
                if (limit.getRowCount() != null) {
                    String placeholder = addParam(ParamType.LIMIT_OFFSET, limit.getRowCount().toString());
                    limit.setRowCount(new LongValue(placeholder));
                }
            }

            private void handleOffset(Offset offset) {
                if (offset.getOffset() != null) {
                    String placeholder = addParam(ParamType.LIMIT_OFFSET, offset.getOffset().toString());
                    offset.setOffset(new LongValue(placeholder));
                }
            }
        };

        expressionDeParser.setSelectVisitor(selectDeParser);
        expressionDeParser.setBuffer(templateBuilder);

        // 处理不同类型的语句
        if (statement instanceof Select) {
            Select select = (Select) statement;
            select.getSelectBody().accept(selectDeParser);
        } else {
            // 处理其他类型语句（INSERT, UPDATE, DELETE）
            templateBuilder.append(statement.toString());
        }

        // 创建模板对象
        SqlTemplate template = new SqlTemplate();
        template.setTemplate(templateBuilder.toString());
        template.setParams(new ArrayList<>(params));
        template.setHash(generateTemplateHash(template));
        template.setNormalizedStructure(normalizeStructure(template));

        return template;
    }

    // 添加参数并返回占位符（用于未关联字段的值）
    public static String addParam(ParamType type, Object value) {
        String placeholder = "{" + type.name().toLowerCase() + "_" + paramCounter++ + "}";
        params.add(new TemplateParam(placeholder, type, value, templateBuilder.length()));
        return placeholder;
    }

    // 生成模板哈希
    public static String generateTemplateHash(SqlTemplate template) {
        // 1. 移除所有占位符
        String withoutParams = template.getTemplate().replaceAll("\\{[^}]+\\}", "");

        // 2. 移除多余空格和换行
        String normalized = withoutParams.replaceAll("\\s+", " ").trim();

        // 3. 生成MD5哈希
        return DigestUtils.md5DigestAsHex(normalized.getBytes());
    }

    // 规范化SQL结构（用于相似性比较）
    public static String normalizeStructure(SqlTemplate template) {
        // 1. 移除具体值，保留结构
        String normalized = template.getTemplate()
                .replaceAll("\\{[^}]+\\}", "{}")
                .replaceAll("\\s+", " ");

        // 2. 标准化关键字
        normalized = normalized.replaceAll("(?i)select", "SELECT")
                .replaceAll("(?i)from", "FROM")
                .replaceAll("(?i)where", "WHERE")
                .replaceAll("(?i)group by", "GROUP BY")
                .replaceAll("(?i)order by", "ORDER BY")
                .replaceAll("(?i)join", "JOIN");

        // 3. 移除多余空格
        normalized = normalized.replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\s*=\\s*", " = ")
                .replaceAll("\\s*\\(\\s*", "(")
                .replaceAll("\\s*\\)\\s*", ")");

        return normalized;
    }

    // 比较两个模板的相似性
    public static double calculateSimilarity(SqlTemplate t1, SqlTemplate t2) {
        // 1. 快速哈希比较
        if (t1.getHash().equals(t2.getHash())) {
            return 1.0;
        }

        // 2. 结构相似性比较
        return calculateStringSimilarity(
                t1.getNormalizedStructure(),
                t2.getNormalizedStructure()
        );
    }

    // 计算字符串相似度（使用Levenshtein距离）
    private static double calculateStringSimilarity(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLength;
    }

    // Levenshtein距离算法
    private static int levenshteinDistance(CharSequence s, CharSequence t) {
        int[][] dp = new int[s.length() + 1][t.length() + 1];

        for (int i = 0; i <= s.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= t.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s.length(); i++) {
            for (int j = 1; j <= t.length(); j++) {
                int cost = (s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1);
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s.length()][t.length()];
    }

    public static String genExplainSQL(Map<String, Object> params) {
        return null;
    }

    public static String validate(String sql) throws JSQLParserException {
        CCJSqlParserUtil.parse(sql);

        // 可选：进行更复杂的验证
        Statement statement = CCJSqlParserUtil.parse(sql);
        if (statement instanceof Select) {
            Select select = (Select) statement;
            // 检查是否包含危险操作
            if (containsDangerousOperations(select)) {
                throw new JSQLParserException("Query contains dangerous operations");
            }else{
                return statement.toString().replaceAll("\\s+", " ").trim();
            }
        }
        return null;
    }

    private static boolean containsDangerousOperations(Select select) {
        // 实现安全检查逻辑
        return false;
    }
}