package com.ai.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.time.LocalDateTime;

@Data
@Entity
public class ChatRecord {
    @Id
    @GeneratedValue
    private Long id;
    private String sessionId;
    private String userMessage;
    @Lob  // 处理长文本
    private String aiResponse;
    private LocalDateTime timestamp;
    // getters/setters
}