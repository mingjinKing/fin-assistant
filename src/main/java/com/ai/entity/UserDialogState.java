package com.ai.entity;

import javax.persistence.*;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "user_dialog_state")
public class UserDialogState {
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_stage", referencedColumnName = "stage_id")
    private DialogStage currentStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_template_id", referencedColumnName = "template_id")
    private DialogTemplate lastTemplate;

    @Lob
    @Column(name = "context_data")
    @Convert(converter = JsonConverter.class)
    private JsonContextData contextData;

    // JSON上下文数据转换器
    @Converter
    public static class JsonConverter implements AttributeConverter<JsonContextData, Clob> {
        @Override
        public Clob convertToDatabaseColumn(JsonContextData attribute) {
            // 实现JSON到Clob的转换
            return null;
        }

        @Override
        public JsonContextData convertToEntityAttribute(Clob dbData) {
            return null;
            // 实现Clob到JSON的转换
        }
    }

    // 上下文数据结构（示例）
    public static class JsonContextData {
        private String currentProduct;
        private List<String> comparedProducts;
        private Map<String, Float> interestScores;

        // getters/setters
    }

    // 省略其他getter/setter
}