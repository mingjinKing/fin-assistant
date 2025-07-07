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


model = fasttext.train_supervised(
    input='training_data_unique.txt',
    epoch=150,
    lr=0.15,
    wordNgrams=2,
    dim=300,
    bucket=200000,
    thread=4
)

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
    label, prob = model.predict(query, k=1)
    print(f"查询: '{query}'")
    print(f"预测: {label[0].replace('__label__', '')}, 置信度: {prob[0]:.4f}\n")


model.save_model("query_classifier.bin")