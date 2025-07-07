import requests
import numpy as np

# API配置
API_URL = "http://localhost:5000/vectorize"

def get_vectors(sentences):
    """调用API获取句子向量"""
    payload = {"sentences": sentences}
    response = requests.post(API_URL, json=payload)
    if response.status_code == 200:
        result = response.json()
        return np.array(result['vectors'])
    else:
        print(f"请求失败: {response.status_code}, {response.text}")
        return None