package com.ai.util;

import cn.hutool.core.util.ObjUtil;
import com.ai.entity.UserSession;
import com.ai.exception.ProductNotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonUtils {

    /**
     * 从 Markdown 文本中提取 SQL 代码块内容
     * @param markdownText 包含 Markdown 代码块的文本
     * @return 纯 SQL 字符串，如果没有找到代码块则返回原始文本
     */
    public static String extractSqlFromMarkdown(String markdownText, String language) {
        // 正则表达式匹配 Markdown 代码块（支持带语言标识符或不带标识符）
        String regex = "```(?:" + language + ")?\\s*([\\s\\S]*?)```";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(markdownText);

        if (matcher.find()) {
            // 提取匹配的 SQL 内容并去除首尾空白
            return matcher.group(1).trim();
        }

        // 如果没有找到代码块，返回原始文本（可根据需求改为抛出异常）
        return markdownText.trim();
    }

    public static String extractProductId(String input, UserSession session) throws Exception{
        // 优先从会话中获取最近关注的产品
        if (ObjUtil.isNotEmpty(session.getUserInterestedProduct())) {
            int index = session.getUserInterestedProduct().size() - 1;
            return session.getUserInterestedProduct().get(index);
        }

        // 使用大模型NER识别
        String nerPrompt = String.format("请严格按以下规则从用户输入中提取产品标识符：\n" +
                "1. **提取范围**：仅限理财产品ID、产品全称/简称、归属公司全称/简称、所属行业\n" +
                "2. **输出要求**：\n" +
                "   - 仅返回标准JSON字符串，无任何额外文本\n" +
                "   - JSON结构：{\"product_ids\":[], \"product_names\":[]}\n" +
                "   - 空字段返回空数组\n" +
                "3. **处理规则**：\n" +
                "   - 公司名称需同时返回标准全称和市场简称（如\"工行\"→[\"工商银行\",\"中国工商银行\"]）\n" +
                //"   - 产品名称保留原始表述（如\"朝朝盈\"）\n" +
                "   - 行业术语标准化（如\"科技\"→\"信息技术\"）\n" +
                "   - 同义词合并（如\"工行/工商银行\"视为同一实体）\n" +
                "\n" +
                "示例：\n" +
                "输入：\"介绍下工行的产品\"\n" +
                "输出：{\"product_ids\":[], \"product_names\":[]}\n" +
                "用户输入为：%s",  input);
        String response = DeepSeekClientUtils.getDeepSeekContentWithChatModel(nerPrompt);

        if (StringUtils.isBlank(response)) {
            throw new ProductNotFoundException("无法识别产品: " + input);
        }
        return response;
    }

    public static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
