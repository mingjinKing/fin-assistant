import requests
import json
import time

# API配置
API_URL = "http://localhost:5000/vectorize"

def test_vectorization():
    """测试向量化API"""
    sentences = [
        "自然语言处理是人工智能的重要领域",
        "深度学习模型可以理解人类语言",
        "向量表示有助于语义理解"
    ]

    payload = {"sentences": sentences}

    print("发送请求...")
    start_time = time.time()
    response = requests.post(API_URL, json=payload)
    elapsed = time.time() - start_time

    if response.status_code == 200:
        result = response.json()
        print(f"成功! 耗时: {elapsed:.2f}秒")
        print(f"返回向量数量: {result['count']}")
        print(f"向量维度: {result['dimension']}维")

        # 打印前两个向量的前5个维度
        for i, vec in enumerate(result['vectors'][:2]):
            print(f"句子 {i+1} 前5维: {vec[:5]}")

        # 保存完整结果
        with open('vector_results.json', 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print("完整结果已保存到 vector_results.json")
    else:
        print(f"请求失败! 状态码: {response.status_code}")
        print(f"错误信息: {response.text}")

def test_health_check():
    """测试健康检查端点"""
    response = requests.get("http://localhost:5000/health")
    print(f"健康检查: {response.status_code}")
    print(response.json())

if __name__ == '__main__':
    print("=== 测试健康检查 ===")
    test_health_check()

    print("\n=== 测试向量化API ===")
    test_vectorization()