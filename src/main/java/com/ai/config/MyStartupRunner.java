package com.ai.config;

import com.ai.service.bi.SchemaVectorService;
import com.ai.service.miluvs.MilvusForResearchReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MyStartupRunner implements CommandLineRunner {

    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private SchemaVectorService schemaVectorService;
    @Autowired
    private MilvusForResearchReportService milvusForResearchReportService;

    @Override
    public void run(String... args) throws Exception {
        // 在这里执行你的初始化逻辑
        System.out.println("项目启动完成，开始执行初始化工作...");
        schemaVectorService.init();
        milvusForResearchReportService.ParseAndSaveReports();
    }
}
