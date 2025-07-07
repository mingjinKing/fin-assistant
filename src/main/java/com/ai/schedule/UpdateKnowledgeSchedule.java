package com.ai.schedule;

import com.ai.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;

@Component
public class UpdateKnowledgeSchedule {

    @Autowired
    private DocumentService documentService;

    //@Scheduled(fixedRate = 86400000) // 每天执行一次
    public void updateKnowledgeBase() throws Exception {
        File knowledgeDir = ResourceUtils.getFile("classpath:knowledge");
        File[] files = knowledgeDir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    documentService.processAndStoreDocument(file.getAbsolutePath());
                }
            }
        }
    }
}
