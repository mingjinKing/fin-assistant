package com.ai.dbOp;

import com.ai.entity.ChatRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {
    List<ChatRecord> findBySessionId(String sessionId);
}