package com.ai.service;

import com.ai.entity.PortfolioData;
import com.ai.service.bi.AdvancedSqlTemplateEngine;
import com.ai.service.bi.ChatBIService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class PortfolioService {

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatBIService chatBIService;
    @Autowired
    private AdvancedSqlTemplateEngine advancedSqlTemplateEngine;

    public List<Map<String, Object>> getPortfolio(String userId) {
        /*// 使用ChatBI查询结构化持仓数据
        String jsonExample = "{\n" +
                "            \"userId\": \"U12345\",\n" +
                "            \"totalAssets\": 1500000.00,\n" +
                "            \"marketVolatility\": 0.25,\n" +
                "            \"industryAverageReturn\": 0.12,\n" +
                "            \"products\": [\n" +
                "                {\n" +
                "                    \"id\": \"F001\",\n" +
                "                    \"name\": \"科技成长基金\",\n" +
                "                    \"type\": \"股票型\",\n" +
                "                    \"value\": 500000.00,\n" +
                "                    \"percentage\": 33.33,\n" +
                "                    \"annualizedReturn\": 0.18,\n" +
                "                    \"riskLevel\": 4\n" +
                "                }\n" +
                "            ]\n" +
                "        }";

        List<String> portfolioJson = chatBIService.naturalLanguageQuery(
                "获取用户" + userId + "的完整持仓数据",
                null,
                jsonExample,
                ""
        );*/
        String realSql = advancedSqlTemplateEngine.genSQL("当前用户"+ userId +"持有的所有投资产品(基金、理财)情况，包括产品标识、产品类型、持有金额、购买时间、当前收益表现，以及对应的产品名称。主要关联持仓信息表、基金产品表和理财产品表");

        return jdbcTemplate.queryForList(realSql);

      /*  try {
            return objectMapper.readValue(result.get(0).get("result").toString(), PortfolioData.class);
        } catch (JsonProcessingException e) {
            log.error("解析 PortfolioData JSON错误", e);
            throw new RuntimeException(e);
        }*/

        // 解析JSON到PortfolioData对象
        //return parsePortfolioJson(portfolioJson);
    }

    private PortfolioData parsePortfolioJson(List<String> json) {
        try {
            log.info("持仓数据: {}", json);
            return objectMapper.readValue(json.get(0), PortfolioData.class);
        } catch (JsonProcessingException e) {
            log.error("持仓数据解析失败", e);
            return new PortfolioData().build(); // 返回空对象
        }
    }
}
