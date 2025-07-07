package com.ai.service;

import com.ai.entity.Intent;
import com.ai.entity.UserProfile;
import org.springframework.stereotype.Service;

import static com.ai.service.PromptService.UserIntent.HOLDING_ANALYSIS;

@Service
public class PromptService {


    // 提示词核心模板（动态部分使用占位符）
    private static final String SYSTEM_PROMPT_TEMPLATE = String.format("**角色定义**  \n" +
            "        你是一名专业严谨的{银行名称}智能理财助手，当前服务用户：{用户ID}。所有回答需严格遵循以下准则：\n" +
            "        \n" +
            "        **核心准则**  \n" +
            "        1. **合规优先**  \n" +
            "           - 禁用预测性表述：禁止使用\"将上涨\"、\"预计收益\"等词汇\n" +
            "           - 禁用保证性表述：禁止使用\"保本\"、\"稳赚\"等词汇\n" +
            "           - 强制风险提示：所有投资类回答必须以\"❗投资有风险，决策需谨慎\"结尾\n" +
            "        2. **信息准确性**  \n" +
            "           - 产品数据必须来自数据库：[产品编号]{产品编码} \n" +
           // "           - 实时数据标注：[更新时间]{更新时间}\n" +
            "        3. **个性化响应**  \n" +
            "           - 用户风险等级：{风险等级}\n" +
            "           - 理财偏好：{理财偏好}\n" +
            "           - 年龄分组：{年龄分组}\n" +
            "        4. **授权机制**  \n" +
            "           {授权状态提示}\n" +
            "        \n" +
            "        **交互协议**  \n" +
            "        1. 当用户意图为{当前意图}时：  \n" +
            "           - {意图处理规则}\n" +
            "        2. 模糊问题必须追问：\"请补充[缺失信息]\"\n" +
            "        3. 回答格式：\n" +
            "           ```markdown\n" +
            "           {产品名称}（{产品代码}）\n" +
            "           ► 最新净值：{数据库字段}（更新于{时间}）\n" +
            "           ► 风险等级：{产品风险}\n" +
            "           ► 匹配度：{用户风险}（您的风险评级）\n" +
            "           ❗投资有风险，决策需谨慎\n" +
            "           ```\n" +
            "        \n" +
            "        **最终声明**  \n" +
            "        ※ 本助手不提供个人投资建议 ※");

    private String buildAuthPrompt(UserProfile user) {
        return user.isAuthorized() ?
                "已登录 - 可提供持仓分析" :
                "未登录 - 禁止提供持仓分析";
    }


    public String buildSystemPrompt(UserProfile user, UserIntent intent) {
        // 获取实时数据示例
        //String latestData = productService.getLatestMarketData();

        return SYSTEM_PROMPT_TEMPLATE
                .replace("{银行名称}", "XX银行")
                .replace("{用户ID}", user.getUserId())
                .replace("{风险等级}", String.valueOf(user.getRiskTolerance()))
                .replace("{理财偏好}", user.getPreferredFundTypes().toString())
                .replace("{年龄分组}", getAgeGroup(user.getAge()))
                .replace("{授权状态提示}", buildAuthPrompt(user))
                .replace("{当前意图}", intent.name())
                .replace("{意图处理规则}", getIntentRules(intent));
                //.replace("{更新时间}", latestData);
    }

    private String getIntentRules(UserIntent intent) {
        switch (intent) {
            case PRODUCT_RECOMMENDATION:
                return "• 最多推荐3个产品\n• 必须标注'非个人建议'\n• 需匹配用户风险等级";
            case HOLDING_ANALYSIS:
                return "• 需要授权状态=✅\n• 使用{产品编码}查询实时数据\n• 隐藏具体收益数值";
            case MARKET_ANALYSIS:
                return "• 仅使用公开数据\n• 禁止预测走势\n• 附加宏观政策链接";
            default:
                return "• 优先检索知识库\n• 信息不足时立即追问";
        }
    }

    // 枚举：用户意图分类
    public enum UserIntent {
        PRODUCT_RECOMMENDATION,
        HOLDING_ANALYSIS,
        MARKET_ANALYSIS,
        GENERAL_QUERY
    }

    private String getAgeGroup(int age) {
        if (age < 30) return "青年客群";
        if (age < 50) return "中年客群";
        return "银发客群";
    }

}
