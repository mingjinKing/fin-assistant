package com.ai.entity;

import lombok.Data;

import javax.persistence.*;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_behavior")
@Data
public class UserBehavior {
    public enum InteractionType {
        CLICK, QUERY, COMPARE, VIEW_DETAIL, ADD_TO_COMPARE, PURCHASE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private InteractionType type;

    @Column(name = "target_id", length = 50)
    private String targetId; // 产品ID/内容ID

    @Lob
    @Column(name = "content")
    private String content; // 原始内容

    @Column(name = "duration")
    private Float duration; // 交互时长(秒)

    @Lob
    @Column(name = "behavior_vector")
    private byte[] behaviorVector; // 768维向量

    // 向量访问辅助方法
    public float[] getBehaviorVector() {
        if (behaviorVector == null) return null;
        return ByteBuffer.wrap(behaviorVector).asFloatBuffer().array();
    }

    public void setBehaviorVector(float[] vector) {
        if (vector == null) {
            behaviorVector = null;
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.asFloatBuffer().put(vector);
        behaviorVector = buffer.array();
    }

    // 省略其他getter/setter
}
