package com.ai.entity;

import lombok.Data;

import javax.persistence.*;
import java.nio.ByteBuffer;
import java.util.Arrays;


@Entity
@Table(name = "dialog_template")
@Data
public class DialogTemplate {
    public enum TemplateType { OPENING, FOLLOWUP, TRANSITION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private DialogStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 20)
    private TemplateType templateType;

    @Column(name = "template_text", nullable = false, length = 500)
    private String templateText;

    @Column(name = "interest_dim", length = 50)
    private String interestDimension;

    @Lob
    @Column(name = "embedding_vector")
    private byte[] embeddingVector; // 存储768维浮点向量

    // 向量访问辅助方法
    public float[] getVector() {
        if (embeddingVector == null) return null;
        return ByteBuffer.wrap(embeddingVector).asFloatBuffer().array();
    }

    public void setVector(float[] vector) {
        if (vector == null) {
            embeddingVector = null;
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.asFloatBuffer().put(vector);
        embeddingVector = buffer.array();
    }

    // 省略其他getter/setter
}
