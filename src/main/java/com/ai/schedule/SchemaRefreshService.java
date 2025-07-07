package com.ai.schedule;

import com.ai.service.bi.SchemaVectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SchemaRefreshService {

    @Autowired
    private SchemaVectorService schemaVectorService;

    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点刷新
    public void refreshSchemas() {
        schemaVectorService.vectorizeAndStoreSchemas();
    }
}
