# train_classifier.py
import fasttext
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split, StratifiedKFold
from sklearn.metrics import (confusion_matrix, classification_report,
                             f1_score, precision_score, recall_score)
import seaborn as sns
from collections import Counter

# 改进的去重函数 - 保留第一条出现的数据
def remove_duplicates(input_file, output_file):
    seen = set()
    unique_lines = []

    with open(input_file, 'r', encoding='utf-8') as f:
        for line in f:
            # 提取文本内容作为判断依据（忽略标签差异）
            content = ' '.join(line.strip().split()[1:])
            if content not in seen:
                seen.add(content)
                unique_lines.append(line)

    with open(output_file, 'w', encoding='utf-8') as f:
        f.writelines(unique_lines)

    return len(unique_lines), len(seen)

# 数据去重
input_file = 'training_data.txt'
output_file = 'training_data_unique.txt'
unique_count, content_unique = remove_duplicates(input_file, output_file)
print(f'原始数据量: {unique_count}, 唯一内容量: {content_unique}')

# 加载数据并分析类别分布
texts = []
labels = []
class_distribution = Counter()

with open(output_file, 'r', encoding='utf-8') as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) < 2:
            continue
        label = parts[0].replace('__label__', '')
        text = ' '.join(parts[1:])
        labels.append(label)
        texts.append(text)
        class_distribution[label] += 1

print("\n类别分布分析:")
for cls, count in class_distribution.items():
    print(f"{cls}: {count} 条 ({count/len(labels):.1%})")

# 分层划分训练集和测试集 (80/20)
X_train, X_test, y_train, y_test = train_test_split(
    texts, labels, test_size=0.2, random_state=42, stratify=labels
)

# 写入训练集和测试集文件
def write_dataset(filename, X, y):
    with open(filename, 'w', encoding='utf-8') as f:
        for text, label in zip(X, y):
            f.write(f'__label__{label} {text}\n')

write_dataset('train_set.txt', X_train, y_train)
write_dataset('test_set.txt', X_test, y_test)

print(f"\n训练集大小: {len(X_train)}")
print(f"测试集大小: {len(X_test)}")

# 模型训练函数
def train_fasttext_model(train_file, test_file, autotune=True):
    if autotune:
        print("\n使用自动调优训练模型...")
        model = fasttext.train_supervised(
            input=train_file,
            autotuneValidationFile=test_file,
            autotuneDuration=10  # 调优5分钟
        )
    else:
        print("\n使用手动参数训练模型...")
        model = fasttext.train_supervised(
            input=train_file,
            epoch=150,
            lr=0.15,
            wordNgrams=2,
            dim=300,
            bucket=200000,
            thread=4
        )
    return model

# 训练模型 (自动调优)
model = train_fasttext_model('train_set.txt', 'test_set.txt', autotune=True)

# 保存模型
model.save_model("query_classifier.bin")
print("模型已保存为 query_classifier.bin")

# 改进的预测函数 - 动态置信阈值
def predict_with_confidence(model, text, k=1):
    # 增加输入合法性检查
    if not text or text.strip() == "":
        return "__label__uncertain", 0.0

    labels, probs = model.predict(text, k=k)
    pred_label = labels[0].replace('__label__', '')
    prob = probs[0]

    # 基于类别的动态置信阈值
    thresholds = {
        'simple': 0.65,
        'medium': 0.70,
        'complex': 0.75
    }

    if prob < thresholds.get(pred_label, 0.7):
        return "__label__uncertain", prob
    return labels[0], prob

# 评估函数
def evaluate_model(model, test_file):
    y_true = []
    y_pred = []
    texts = []
    probs = []

    with open(test_file, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if not parts:
                continue
            true_label = parts[0].replace('__label__', '')
            text = ' '.join(parts[1:])
            pred_label, prob = predict_with_confidence(model, text)

            if pred_label == "__label__uncertain":
                # 对于不确定样本，使用最可能的标签
                labels, _ = model.predict(text, k=1)
                pred_label = labels[0].replace('__label__', '')

            y_true.append(true_label)
            y_pred.append(pred_label)
            texts.append(text)
            probs.append(prob)

    # 分类报告
    print("\n分类报告:")
    print(classification_report(y_true, y_pred, labels=['simple', 'medium', 'complex'], target_names=['simple', 'medium', 'complex']))

    # 混淆矩阵
    cm = confusion_matrix(y_true, y_pred, labels=['simple', 'medium', 'complex'])
    plt.figure(figsize=(10, 8))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                xticklabels=['simple', 'medium', 'complex'],
                yticklabels=['simple', 'medium', 'complex'])
    plt.xlabel('预测标签')
    plt.ylabel('真实标签')
    plt.title('混淆矩阵')
    plt.savefig('confusion_matrix.png')
    plt.show()

    # 置信度分布分析
    plt.figure(figsize=(10, 6))
    for cls in ['simple', 'medium', 'complex']:
        cls_probs = [p for t, p in zip(y_true, probs) if t == cls]
        sns.kdeplot(cls_probs, label=cls, fill=True)
    plt.xlabel('预测置信度')
    plt.ylabel('密度')
    plt.title('各类别置信度分布')
    plt.legend()
    plt.savefig('confidence_distribution.png')

    return y_true, y_pred, texts, probs

# 执行评估
y_true, y_pred, test_texts, probs = evaluate_model(model, 'test_set.txt')

# 识别边界样本
def identify_boundary_samples(texts, y_true, probs, threshold=0.7):
    uncertain_samples = []
    misclassified = []

    for text, true_label, prob in zip(texts, y_true, probs):
        pred_label = model.predict(text, k=1)[0][0].replace('__label__', '')

        # 置信度低的样本
        if prob < threshold:
            uncertain_samples.append((text, true_label, pred_label, prob))

        # 错误分类样本
        if pred_label != true_label:
            misclassified.append((text, true_label, pred_label, prob))

    return uncertain_samples, misclassified

# 获取边界样本
uncertain_samples, misclassified = identify_boundary_samples(test_texts, y_true, probs)

# 保存边界样本
def save_samples(filename, samples):
    with open(filename, 'w', encoding='utf-8') as f:
        for sample in samples:
            text, true_label, pred_label, prob = sample
            f.write(f"True: {true_label}, Pred: {pred_label}, Prob: {prob:.4f}\n{text}\n\n")

save_samples('uncertain_samples.txt', uncertain_samples)
save_samples('misclassified_samples.txt', misclassified)

print(f"\n边界样本分析:")
print(f"- 低置信度样本: {len(uncertain_samples)}")
print(f"- 错误分类样本: {len(misclassified)}")

# 交叉验证函数
def cross_validate(data_file, n_splits=5):
    # 加载数据
    texts, labels = [], []
    with open(data_file, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) < 2:
                continue
            labels.append(parts[0].replace('__label__', ''))
            texts.append(' '.join(parts[1:]))

    # 分层K折交叉验证
    skf = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=42)
    f1_scores = []

    for fold, (train_idx, val_idx) in enumerate(skf.split(texts, labels)):
        print(f"\n=== 交叉验证 Fold {fold+1}/{n_splits} ===")

        # 创建临时训练/验证文件
        train_file = f'fold_{fold}_train.txt'
        val_file = f'fold_{fold}_val.txt'

        with open(train_file, 'w', encoding='utf-8') as f:
            for i in train_idx:
                f.write(f'__label__{labels[i]} {texts[i]}\n')

        with open(val_file, 'w', encoding='utf-8') as f:
            for i in val_idx:
                f.write(f'__label__{labels[i]} {texts[i]}\n')

        # 训练模型
        fold_model = train_fasttext_model(train_file, val_file, autotune=False)

        # 评估模型
        _, y_pred, _, _ = evaluate_model(fold_model, val_file)
        y_true_fold = [labels[i] for i in val_idx]

        # 计算F1分数
        f1 = f1_score(y_true_fold, y_pred, average='weighted')
        f1_scores.append(f1)
        print(f"Fold {fold+1} F1分数: {f1:.4f}")

    # 汇总结果
    print("\n交叉验证结果:")
    print(f"平均F1分数: {np.mean(f1_scores):.4f}")
    print(f"F1分数标准差: {np.std(f1_scores):.4f}")
    return f1_scores

# 执行交叉验证
print("\n开始交叉验证...")
f1_scores = cross_validate('training_data_unique.txt', n_splits=5)

# 最终模型训练 (使用全部数据)
print("\n训练最终模型 (使用全部数据)...")
final_model = train_fasttext_model('training_data_unique.txt', 'test_set.txt', autotune=True)
final_model.save_model("final_query_classifier.bin")

# 测试最终模型
test_queries = [
    "显示当前账户余额",
    "近三个月收益趋势如何",
    "根据我的风险偏好优化投资组合",
    "比较科技和消费行业基金的表现",
    "压力测试：利率上升200基点的影响"
]

print("\n最终模型测试:")
for query in test_queries:
    label, prob = predict_with_confidence(final_model, query)
    print(f"查询: '{query}'")
    print(f"预测: {label.replace('__label__', '')}, 置信度: {prob:.4f}\n")