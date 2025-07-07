package com.ai.service.data;

import cn.hutool.core.util.ObjUtil;
import com.ai.entity.FundConstants;
import com.ai.entity.ResearchReport;
import com.ai.service.miluvs.MilvusForResearchReportService;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import io.github.pigmesh.ai.deepseek.core.chat.Delta;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Log4j2
public class ReportGenerator {

    @Autowired
    private DeepSeekClient deepSeekApi;

    @Autowired
    private MilvusForResearchReportService milvusService;

    private static final String REPORT_TEMPLATE =
            "请生成一份关于【%s】行业的基金研究报告，包含以下要素：\n" +
                    "1. 行业现状分析（不少于1000字）\n" +
                    "2. 头部基金产品表现（虚构3个基金产品）\n" +
                    "3. 未来3年发展趋势预测\n" +
                    "4. 投资风险提示\n\n" +
                    "5. 尾部funds（虚构3个基金产品）\n\n" +
                    "输出格式要求：使用Markdown格式，包含二级标题";

    private final Random random = new Random();

    public void generateAndSaveReport() throws IOException {
        // 1. 准备报告数据
        ResearchReport report = new ResearchReport();
        report.setReportId(UUID.randomUUID().toString());
        String industry = getRandomIndustry();
        report.setIndustry(industry);
        report.setTitle(industry + "行业基金投资分析报告");

        // 2. 调用大模型生成内容
        String prompt = String.format(REPORT_TEMPLATE, industry);
        StringBuilder fullResponse = new StringBuilder();
        log.info("Generating report prompt is: " + prompt);

        Flux<ChatCompletionResponse> response = deepSeekApi.chatFluxCompletion(prompt)
                .cast(ChatCompletionResponse.class) // 确保类型为String
                .doOnNext(chunk -> {
                    Delta delta = chunk.choices().get(0).delta();
                    String item = ObjUtil.isEmpty(delta) ? "" : delta.content();
                    if(StringUtils.isNotEmpty(item)) {
                        fullResponse.append(item);
                    }
                }).doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE || signal == SignalType.ON_ERROR) {
                        CompletableFuture.runAsync(() -> {
                            report.setContent(fullResponse.toString());
                            // 3. 写入Markdown文件
                            String filePath = saveToMarkdown(report);
                            report.setFilePath(filePath);
                        });

                    }
                });
        response.subscribe();



        // 4. 存储到Milvus
       milvusService.saveReport(report);
    }

    private String getRandomIndustry() {
        List<String> industries = FundConstants.FUND_INDUSTRIES;
        return industries.get(random.nextInt(industries.size()));
    }


    private String saveToMarkdown(ResearchReport report) {
        String fileName = report.getTitle() + ".md";
        Path dirPath = Paths.get("src", "main", "resources", "knowledge", "report");
        log.info("dirPath is: {}", dirPath);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String filePath = dirPath.resolve(fileName).toString();

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("# " + report.getTitle() + "\n\n");
            writer.write("**报告ID:** " + report.getReportId() + "\n\n");
            writer.write("**行业分类:** " + report.getIndustry() + "\n\n");
            writer.write(report.getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return filePath;
    }
}
