package com.ai.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// 增强的实体类定义
@Data
public class SqlQueryModel {
    private String operation;
    private List<Table> tables = new ArrayList<>();
    private List<Field> fields = new ArrayList<>();
    private ConditionGroup conditions;
    private List<String> groupBy = new ArrayList<>();
    private List<Condition> having = new ArrayList<>();
    private List<Order> orderBy = new ArrayList<>();
    private Integer limit;
    private Subquery subquery;

    public SqlQueryModel(){
        this.operation = "SELECT";
        this.tables = Collections.singletonList(new Table());
        this.fields = Collections.singletonList(new Field());
        this.conditions = new ConditionGroup();
        this.groupBy = Collections.singletonList("");
        this.having = Collections.singletonList(new Condition());
        this.orderBy = Collections.singletonList(new Order());
        this.limit = 0;
        this.subquery = new Subquery();
    }

    @Data
    public static class Table {
        private String name;
        private String alias;
        private String joinType; // MAIN, INNER, LEFT, RIGHT
        private String onClause;
    }

    @Data
    public static class Field {
        private String expr;
        private String alias;
        private boolean aggregate;
    }

    @Data
    public static class ConditionGroup {
        private String type; // AND/OR
        private List<Object> conditions = new ArrayList<>(); // Condition or ConditionGroup
    }

    @Data
    public static class Condition {
        private String field;
        private String operator;
        private Object value;
        private String valueType; // STRING, NUMBER, DATE, BOOLEAN
    }

    @Data
    public static class Order {
        private String field;
        private String direction;
    }

    @Data
    public static class Subquery {
        private boolean exists;
        private SqlQueryModel query;
    }

    // 在SqlQueryModel类中添加
    public List<Table> getSortedTables() {
        List<Table> sorted = new ArrayList<>(tables);
        sorted.sort(Comparator.comparing(t -> t.getJoinType() != null));
        return sorted;
    }
}
