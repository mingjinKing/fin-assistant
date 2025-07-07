import java.util.*;

public class ResearchReportGenerator {

    public static void main(String[] args) {
        List<Map<String, Object>> researchReports = new ArrayList<>();

        // 生成10份基金研报测试数据
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> report = new HashMap<>();
            Map<String, Object> metadata = new HashMap<>();

            // 研报核心内容
            report.put("text", generateReportContent(i));

            // 元数据
            metadata.put("knowledge_type", "fund_research_report");
            metadata.put("product_category", getRandomProductCategory());
            metadata.put("risk_level", getRandomRiskLevel());
            metadata.put("key_terms", generateKeyTerms(i));

            report.put("metadata", metadata);
            researchReports.add(report);
        }

        // 调用MilvusService插入数据 (需在实际Spring环境中运行)
        // milvusService.insertKnowledge(researchReports);

        // 打印生成的数据
        System.out.println("Generated research reports:");
        researchReports.forEach(System.out::println);
    }

    private static String generateReportContent(int id) {
        String[] themes = {"科技成长", "消费升级", "新能源", "医药健康", "高端制造"};
        String theme = themes[(id - 1) % themes.length];

        return String.format("基金研报#%d: 本季度重点推荐关注%s主题基金。随着政策红利持续释放，%s领域龙头企业估值优势明显，" +
                        "建议投资者采取定投策略分批布局。预计未来6个月该板块有15%%-20%%的上涨空间。",
                id, theme, theme);
    }

    private static String getRandomProductCategory() {
        String[] categories = {"股票型基金", "混合型基金", "债券型基金", "指数型基金", "QDII基金"};
        return categories[new Random().nextInt(categories.length)];
    }

    private static String getRandomRiskLevel() {
        String[] levels = {"高", "中高", "中", "中低", "低"};
        return levels[new Random().nextInt(levels.length)];
    }

    private static List<String> generateKeyTerms(int id) {
        List<List<String>> termSets = Arrays.asList(
                Arrays.asList("科技龙头", "半导体", "5G应用", "云计算", "创新药"),
                Arrays.asList("消费升级", "白酒", "家电", "电商", "新零售"),
                Arrays.asList("新能源车", "光伏", "锂电池", "碳中和", "储能"),
                Arrays.asList("医疗器械", "生物科技", "CXO", "疫苗", "基因测序"),
                Arrays.asList("工业4.0", "智能制造", "机器人", "专精特新", "国产替代")
        );
        return termSets.get((id - 1) % termSets.size());
    }

}
