package com.ai.controller;

import com.ai.service.KnowledgeBaseService;
import com.ai.service.miluvs.MilvusForResearchReportService;
import com.ai.service.PortfolioService;
import com.ai.service.QAService;
import com.ai.service.data.ReportGenerator;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import io.github.pigmesh.ai.deepseek.core.models.ModelsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatDemoController {

    @Autowired
    private QAService qaService;
    @Autowired
    private DeepSeekClient deepSeekClient;
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private ReportGenerator reportGenerator;
    @Autowired
    private MilvusForResearchReportService milvusForResearchReportService;
    @Autowired
    private PortfolioService portfolioService;

    @GetMapping(value = "initKnowledge")
    public void initKnowledge() throws Exception {
        knowledgeBaseService.processAndStoreKnowledgeBase();
    }

    @GetMapping(value = "parseAndSaveReport")
    public void parseAndSaveReport() throws Exception {
        milvusForResearchReportService.ParseAndSaveReports();
    }

    @GetMapping(value = "genReport")
    public void genReport() throws Exception {
        for (int i = 0; i < 10; i++){
            reportGenerator.generateAndSaveReport();
        }
    }

    @GetMapping(value = "getUserProfile")
    public Object getUserProfile() throws Exception {
        return portfolioService.getPortfolio("U001");
    }

    @GetMapping(value = "searchKnowLedge")
    public Object searchKnowLedge() throws Exception {
        return  milvusForResearchReportService.searchSimilarReports("消费类基金、医疗类基金", 5);
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + "; charset=UTF-8")
    public Flux<ChatCompletionResponse> chat(String prompt) throws Exception{
        return qaService.answerQuestion(prompt);
    }

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelsResponse models() {
        return deepSeekClient.models();
    }

}
