# data_augment.py
import random
import jieba
from synonyms import nearby

# 同义词替换增强
def synonym_replacement(sentence, n=2):
    words = list(jieba.cut(sentence))
    new_sentence = sentence
    if len(words) > 3:  # 避免短句过度替换
        indices = random.sample(range(len(words)), min(n, len(words)//2))
        for i in indices:
            syns = nearby(words[i])[0]
            if syns and syns[0] != words[i]:
                words[i] = syns[0]
        new_sentence = ''.join(words)
    return new_sentence

# 金融同义词库
financial_synonyms = {
    "基金": ["理财产品", "投资产品", "资管产品"],
    "股票": ["个股", "权益资产", "股份"],
    "收益": ["回报", "盈利", "获利"],
    "风险": ["波动", "不确定性", "敞口"]
}

# 领域特定增强
def finance_specific_augment(query):
    for term, syns in financial_synonyms.items():
        if term in query:
            query = query.replace(term, random.choice(syns))
    return query

# 生成增强数据
base_data = [
    ("__label__simple", "查看余额"),
    ("__label__medium", "过去三个月收益"),
    ("__label__complex", "优化投资组合")
]

augmented_data = []
for label, query in base_data:
    # 基础增强
    augmented_data.append((label, query))
    augmented_data.append((label, query + "？"))  # 加标点

    # 同义替换
    augmented_data.append((label, synonym_replacement(query)))

    # 金融术语替换
    augmented_data.append((label, finance_specific_augment(query)))

    # 添加修饰词
    modifiers = ["请", "帮我", "我想要", "可以"]
    for mod in modifiers:
        augmented_data.append((label, f"{mod}{query}"))

# 输出训练文件
with open("enhanced_training.txt", "w") as f:
    for label, query in augmented_data:
        f.write(f"{label} {query}\n")