from utils import get_vectors
from sklearn.metrics.pairwise import cosine_similarity


def test_semantic_search(query, sentences, vectors):
    """测试语义搜索效果"""
    print(f"\n=== 语义搜索测试: '{query}' ===")

    # 获取查询向量
    query_vector = get_vectors([query])[0]

    # 计算相似度
    similarities = cosine_similarity([query_vector], vectors)[0]

    # 获取最相似的5个结果
    results = sorted(
        zip(sentences, similarities),
        key=lambda x: x[1],
        reverse=True
    )[:5]

    # 打印结果
    print(f"查询: '{query}'")
    print("最相关结果:")
    for i, (sentence, sim) in enumerate(results, 1):
        print(f"{i}. {sentence} (相似度: {sim:.4f})")