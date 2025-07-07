import matplotlib.pyplot as plt
from sklearn.manifold import TSNE
from sklearn.cluster import KMeans

def visualize_clusters(vectors, sentences):
    """可视化句子向量聚类"""
    print("\n=== 聚类可视化 ===")

    # 使用t-SNE降维
    tsne = TSNE(n_components=2, perplexity=min(5, len(vectors) - 1), random_state=42)
    vectors_2d = tsne.fit_transform(vectors)

    # K-means聚类
    n_clusters = 3
    kmeans = KMeans(n_clusters=n_clusters, random_state=42)
    clusters = kmeans.fit_predict(vectors)

    # 绘制结果
    plt.figure(figsize=(12, 8))
    scatter = plt.scatter(
        vectors_2d[:, 0],
        vectors_2d[:, 1],
        c=clusters,
        cmap='viridis',
        alpha=0.7
    )

    # 添加标签
    for i, sentence in enumerate(sentences):
        plt.annotate(
            f"{i}:{sentence[:10]}",
            (vectors_2d[i, 0], vectors_2d[i, 1]),
            fontsize=9
        )

    plt.colorbar(scatter, label='聚类')
    plt.title('句子向量聚类可视化 (t-SNE)')
    plt.xlabel('维度 1')
    plt.ylabel('维度 2')
    plt.grid(alpha=0.2)
    plt.savefig('sentence_clusters.png', dpi=300)
    print("聚类可视化图已保存为 sentence_clusters.png")