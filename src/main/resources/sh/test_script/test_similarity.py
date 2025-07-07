import numpy as np
from sklearn.metrics.pairwise import cosine_similarity

def test_similarity(vectors, sentences):
    """测试相似句子的向量相似度"""
    print("\n=== 相似度测试 ===")

    # 计算所有向量之间的余弦相似度
    sim_matrix = cosine_similarity(vectors)

    # 打印相似度矩阵
    print("相似度矩阵:")
    for i, row in enumerate(sim_matrix):
        print(f"{sentences[i][:15]}... | " + " ".join([f"{s:.3f}" for s in row]))

    # 分析特定句子对
    test_pairs = [
        ("我喜欢吃苹果", "苹果是我喜欢的水果"),
        ("我喜欢吃苹果", "计算机科学很有趣"),
        ("深度学习模型", "神经网络架构")
    ]

    for sent1, sent2 in test_pairs:
        idx1 = sentences.index(sent1) if sent1 in sentences else -1
        idx2 = sentences.index(sent2) if sent2 in sentences else -1

        if idx1 >= 0 and idx2 >= 0:
            similarity = sim_matrix[idx1][idx2]
            print(f"'{sent1}' vs '{sent2}': {similarity:.4f}")