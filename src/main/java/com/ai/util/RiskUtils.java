package com.ai.util;

public class RiskUtils {

    /**
     * 将数字风险等级转换为中文描述
     * @param level 风险等级值 (1-5)
     * @return 中文风险描述
     */
    public static String formatRiskLevel(double level) {
        int intLevel = (int) Math.round(level); // 四舍五入取整

        switch (intLevel) {
            case 1: return "低风险";
            case 2: return "中低风险";
            case 3: return "中风险";
            case 4: return "中高风险";
            case 5: return "高风险";
            default:
                // 处理小数风险等级
                if (level >= 4.5) return "极高风险";
                if (level >= 3.5) return "中高风险";
                if (level >= 2.5) return "中风险";
                if (level >= 1.5) return "中低风险";
                return "低风险";
        }
    }

    /**
     * 获取风险等级的颜色标识
     * @param level 风险等级值
     * @return 颜色代码
     */
    public static String getRiskColor(double level) {
        if (level >= 4.5) return "#FF0000"; // 红色
        if (level >= 3.5) return "#FF6600"; // 橙色
        if (level >= 2.5) return "#FFCC00"; // 黄色
        if (level >= 1.5) return "#00CC66"; // 浅绿
        return "#009900"; // 深绿
    }

    /**
     * 获取风险等级的图标标识
     * @param level 风险等级值
     * @return 图标Unicode
     */
    public static String getRiskIcon(double level) {
        if (level >= 4.5) return "🔥"; // 火焰
        if (level >= 3.5) return "⚡"; // 闪电
        if (level >= 2.5) return "🌊"; // 波浪
        if (level >= 1.5) return "🌱"; // 幼苗
        return "⛱️"; // 沙滩伞
    }

    /**
     * 生成完整的风险描述
     * @param level 风险等级值
     * @return 带图标和颜色的风险描述
     */
    public static String formatFullRiskDescription(double level) {
        return String.format(
                "<span style='color:%s'>%s %s</span>",
                getRiskColor(level),
                getRiskIcon(level),
                formatRiskLevel(level)
        );
    }
}