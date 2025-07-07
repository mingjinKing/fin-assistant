package com.ai.service.intent;

import com.ai.entity.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

// 响应生成器
@Service
public class ResponseGenerator {

    public AssistantResponse generateResponse(List<Intent> intents, Map<String, ServiceResult> results) {
        AssistantResponse response = new AssistantResponse();
        response.setType("composite");

        List<ResponsePart> parts = new ArrayList<>();
        StringBuilder textResponse = new StringBuilder();

        // 1. 生成文本摘要
        if (intents.size() > 1) {
            textResponse.append("根据您的需求，我为您整理了以下信息：\n\n");
        }

        // 2. 按意图顺序组装响应
        for (Intent intent : intents) {
            ServiceResult result = results.get(intent.getType());
            if (result == null || !result.isSuccess()) continue;

            switch (intent.getType()) {
                case "行业行情分析":
                    Map<String, Object> marketData = (Map<String, Object>) result.getData();
                    textResponse.append(generateMarketText(marketData, intent));
                    parts.add(generateMarketChart(marketData, intent));
                    break;

                case "持仓收益查询":
                    List<Map<String, Object>> portfolioData = (List<Map<String, Object>>) result.getData();
                    textResponse.append(generatePortfolioText(portfolioData, intent));
                    parts.add(generatePortfolioTable(portfolioData, intent));
                    break;

                // 其他响应类型...
            }
        }

        // 3. 设置响应内容
        response.setTextResponse(textResponse.toString());
        response.setParts(parts);

        return response;
    }

    private String generateMarketText(Map<String, Object> data, Intent intent) {
        return String.format("📈 **%s类基金行情**\n" +
                        "- 平均日收益: %.2f%%\n" +
                        "- 表现最佳基金收益: %.2f%%\n" +
                        "- 相关基金数量: %d\n\n",
                intent.getParams().getString("industry"),
                data.get("avg_daily_return"),
                data.get("top_performer_return"),
                data.get("fund_count"));
    }

    private ResponsePart generateMarketChart(Map<String, Object> data, Intent intent) {
        ResponsePart chart = new ResponsePart();
        chart.setType("chart");
        chart.setTitle(intent.getParams().getString("industry") + "基金行情");

        ChartData chartData = new ChartData();
        chartData.setType("bar");
        chartData.setLabels(Arrays.asList("平均收益", "最佳收益"));
        chartData.setDatasets(Collections.singletonList(
                new ChartDataset("收益率(%)",
                        Arrays.asList(data.get("avg_daily_return"), data.get("top_performer_return")),
                        "#36A2EB")
        ));

        chart.setData(chartData);
        return chart;
    }

    private String generatePortfolioText(List<Map<String, Object>> data, Intent intent) {
        if (data.isEmpty()) {
            return "💰 您当前没有持有" + intent.getParams().getString("industry") + "类基金\n\n";
        }

        StringBuilder sb = new StringBuilder("💰 **您的持仓基金表现**\n");
        for (Map<String, Object> fund : data) {
            sb.append(String.format("- %s: %.2f元 (%.2f%%)\n",
                    fund.get("FUND_NAME"),
                    fund.get("PROFIT"),
                    fund.get("PROFIT_RATE")));
        }
        sb.append("\n");
        return sb.toString();
    }

    private ResponsePart generatePortfolioTable(List<Map<String, Object>> data, Intent intent) {
        ResponsePart table = new ResponsePart();
        table.setType("table");
        table.setTitle("持仓基金详情");

        TableData tableData = new TableData();
        tableData.setColumns(Arrays.asList("基金名称", "持仓金额", "当前收益", "收益率"));

        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> fund : data) {
            rows.add(Arrays.asList(
                    fund.get("FUND_NAME"),
                    String.format("%.2f元", ((BigDecimal) fund.get("HOLD_AMOUNT")).doubleValue()),
                    String.format("%.2f元", ((BigDecimal) fund.get("PROFIT")).doubleValue()),
                    String.format("%.2f%%", ((BigDecimal) fund.get("PROFIT_RATE")).doubleValue())
            ));
        }

        tableData.setRows(rows);
        table.setData(tableData);
        return table;
    }
}
