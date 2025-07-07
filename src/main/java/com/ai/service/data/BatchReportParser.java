package com.ai.service.data;

import cn.hutool.core.util.ObjUtil;
import com.ai.entity.ResearchReport;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import io.github.pigmesh.ai.deepseek.core.chat.Delta;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.io.IOException;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BatchReportParser {

    @Autowired
    private DeepSeekClient deepSeekClient;

    // 批量解析报告目录
    public List<ResearchReport> parseReportsDirectory(String directoryPath) throws IOException {
        List<ResearchReport> reports = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(directoryPath), "*.md")) {
            for (Path entry : stream) {
                try {
                    ResearchReport report = parseMarkdown(entry.toString());
                    String content = report.getContent();
                    StringBuilder summary = new StringBuilder();

                    // 创建每个文件的异步任务
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    futures.add(future);

                    String prompt = String.format("请根据内容：\n %s 生成一个总结，并使用中文进行总结。\n要求：只输出总结内容", content);
                    Flux<ChatCompletionResponse> response = deepSeekClient.chatFluxCompletion(prompt)
                            .cast(ChatCompletionResponse.class)
                            .doOnNext(chunk -> {
                                Delta delta = chunk.choices().get(0).delta();
                                String item = ObjUtil.isEmpty(delta) ? "" : delta.content();
                                if (StringUtils.isNotEmpty(item)) {
                                    summary.append(item);
                                }
                            })
                            .doFinally(signal -> {
                                if (signal == SignalType.ON_COMPLETE || signal == SignalType.ON_ERROR) {
                                    report.setSummary(summary.toString());
                                    reports.add(report); // 添加报告到结果列表
                                    future.complete(null); // 标记任务完成
                                }
                            });
                    response.subscribe();
                    System.out.println("开始解析: " + entry.getFileName());
                } catch (Exception e) {
                    System.err.println("解析失败 [" + entry.getFileName() + "]: " + e.getMessage());
                }
            }
        }

        // 等待所有异步任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return reports;
    }
    private void generateSummary(ResearchReport report) {
        String content = report.getContent();
        StringBuilder summary = new StringBuilder();
        String prompt = String.format("请根据内容：\n " +
                "%s" +
                "生成一个总结，并使用中文进行总结。\n" +
                "要求：只输出总结内容", content);
        Flux<ChatCompletionResponse> response = deepSeekClient.chatFluxCompletion(prompt)
                .cast(ChatCompletionResponse.class) // 确保类型为String
                .doOnNext(chunk -> {
                    Delta delta = chunk.choices().get(0).delta();
                    String item = ObjUtil.isEmpty(delta) ? "" : delta.content();
                    if(StringUtils.isNotEmpty(item)) {
                        summary.append(item);
                    }
                }).doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE || signal == SignalType.ON_ERROR) {
                        CompletableFuture.runAsync(() -> {
                            report.setSummary(summary.toString());
                        });

                    }
                });
        response.subscribe();
    }

    // 解析单个MD文件
    private static ResearchReport parseMarkdown(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        ResearchReport report = new ResearchReport();
        // 尝试多种匹配模式
        boolean matched = false;

        // 模式1：带markdown代码块的格式
        Pattern pattern1 = Pattern.compile(
                "#\\s+(.*?)\\s*[\r\n]+" +                   // 标题
                        "(?:\\*\\*报告ID:\\*\\*|报告ID[:：])\\s*(\\S+)\\s*[\r\n]+" +  // 报告ID（支持中文冒号）
                        "(?:\\*\\*行业分类:\\*\\*|行业[:：])\\s*(.*?)\\s*[\r\n]+" +    // 行业分类
                        "```\\s*markdown\\s*[\r\n]+([\\s\\S]*?)```", // 报告内容
                Pattern.DOTALL | Pattern.MULTILINE
        );

        // 模式2：不带代码块的简单格式
        Pattern pattern2 = Pattern.compile(
                "#\\s+(.*?)\\s*[\r\n]+" +                   // 标题
                        "(?:\\*\\*报告ID:\\*\\*|报告ID[:：])\\s*(\\S+)\\s*[\r\n]+" +  // 报告ID
                        "(?:\\*\\*行业分类:\\*\\*|行业[:：])\\s*(.*?)\\s*[\r\n]+" +    // 行业分类
                        "([\\s\\S]*)$",                             // 整个剩余内容
                Pattern.DOTALL | Pattern.MULTILINE
        );

        // 尝试匹配第一种格式
        Matcher matcher = pattern1.matcher(content);
        if (matcher.find()) {
            report.setTitle(matcher.group(1).trim());
            report.setReportId(matcher.group(2).trim());
            report.setIndustry(matcher.group(3).trim());
            report.setContent(matcher.group(4).trim());
            matched = true;
        }
        // 尝试匹配第二种格式
        else {
            matcher = pattern2.matcher(content);
            if (matcher.find()) {
                report.setTitle(matcher.group(1).trim());
                report.setReportId(matcher.group(2).trim());
                report.setIndustry(matcher.group(3).trim());
                report.setContent(matcher.group(4).trim());
                matched = true;
            }
        }

        if (!matched) {
            throw new IOException("无法解析该文件");
        }
        // 设置其他字段
        report.setConfidenceScore(calculateConfidence(report.getContent()));
        report.setFilePath(filePath);

        return report;
    }

    // 根据内容计算置信度（示例逻辑）
    private static Float calculateConfidence(String content) {
        // 示例：根据内容长度和关键词计算置信度
        double score = 0.7; // 基础分

        // 内容长度加分
        int length = content.length();
        if (length > 3000) score += 0.2;
        else if (length > 1500) score += 0.1;

        // 关键词检测加分
        String[] keywords = {"风险", "分析", "预测", "趋势"};
        for (String kw : keywords) {
            if (content.contains(kw)) score += 0.025;
        }

        // 确保在0.5-1.0范围内
        return BigDecimal.valueOf(Math.max(0.5, Math.min(0.99, score)))
                .setScale(2, BigDecimal.ROUND_HALF_UP).floatValue();
    }
}