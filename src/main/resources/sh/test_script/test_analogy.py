def test_analogy(a, b, c, sentences, vectors, top_k=3):
    """测试向量空间中的类比关系"""
    print(f"\n=== 类比推理测试: {a} -> {b} 如同 {c} -> ? ===")

    # 获取向量
    vec_a = vectors[sentences.index(a)]
    vec_b = vectors[sentences.index(b)]
    vec_c = vectors[sentences.index(c)]

    # 计算类比向量: vec_d = vec_b - vec_a + vec_c
    query_vector = vec_b - vec_a + vec_c

    # 找到最相似的向量
    similarities = cosine_similarity([query_vector], vectors)[0]

    # 排除原始句子
    exclude_indices = [sentences.index(s) for s in [a, b, c]]
    for idx in exclude_indices:
        similarities[idx] = -1  # 设置为负值确保不会被选中

    # 获取最相似的结果
    results = sorted(
        zip(sentences, similarities),
        key=lambda x: x[1],
        reverse=True
    )[:top_k]

    print("最可能的结果:")
    for i, (sentence, sim) in enumerate(results, 1):
        print(f"{i}. {sentence} (相似度: {sim:.4f})")