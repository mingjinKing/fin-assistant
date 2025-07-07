from utils import get_vectors
import numpy as np
import matplotlib.pyplot as plt
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.manifold import TSNE
from sklearn.cluster import KMeans
from test_similarity import test_similarity
from test_semantic_search import test_semantic_search
from test_analogy import test_analogy
from visualize_clusters import visualize_clusters

def main():
    # 测试句子集
    sentences = [
        "我喜欢吃苹果",
        "苹果是一种水果",
        "香蕉是我最喜欢的水果",
        "苹果公司发布了新iPhone",
        "科技公司正在开发新手机",
        "深度学习模型用于自然语言处理",
        "人工智能正在改变世界",
        "机器学习是人工智能的子领域",
        "苹果和香蕉都是水果",
        "微软是一家科技巨头",
        "神经网络用于图像识别",
        "水果富含维生素和矿物质"
    ]

    # 获取向量
    print("获取句子向量...")
    vectors = get_vectors(sentences)
    if vectors is None:
        return

    print(f"获取到 {len(vectors)} 个句子的向量，维度: {vectors.shape[1]}")

    # 运行测试套件
    test_similarity(vectors, sentences)
    visualize_clusters(vectors, sentences)

    # 语义搜索测试
    test_semantic_search("水果的好处", sentences, vectors)
    test_semantic_search("科技公司", sentences, vectors)

    # 类比推理测试
    """ test_analogy(
         "苹果是一种水果", "水果",
         "香蕉是我最喜欢的水果",
        sentences, vectors
    )

    test_analogy(
        "苹果公司发布了新iPhone", "科技公司",
        "微软",
        sentences, vectors
    ) """

# 前面定义的测试函数放在这里...

if __name__ == "__main__":
    main()