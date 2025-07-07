# combined_service.py
from flask import Flask, request, jsonify
import fasttext
from transformers import AutoModel, AutoTokenizer
import torch
import numpy as np
import logging
import os

# 初始化Flask应用
app = Flask(__name__)
app.logger.setLevel(logging.INFO)

# 加载分类模型
CLASSIFIER_MODEL_PATH = "./fastText/query_classifier.bin"
classifier_model = fasttext.load_model(CLASSIFIER_MODEL_PATH)

# 加载向量模型
# 获取当前脚本所在目录的绝对路径
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
VECTOR_MODEL_PATH = os.path.join(BASE_DIR, "text2vec-base-chinese")
tokenizer = None
vector_model = None

def load_vector_model():
    """加载向量模型和分词器"""
    global tokenizer, vector_model
    app.logger.info(f"VECTOR_MODEL_PATH is {VECTOR_MODEL_PATH}")

    if not os.path.exists(VECTOR_MODEL_PATH):
        raise FileNotFoundError(f"模型路径不存在: {VECTOR_MODEL_PATH}")
    app.logger.info("开始加载向量模型...")
    tokenizer = AutoTokenizer.from_pretrained(VECTOR_MODEL_PATH, local_files_only=True)
    vector_model = AutoModel.from_pretrained(VECTOR_MODEL_PATH, local_files_only=True)
    app.logger.info("向量模型加载完成!")

@app.route('/classify', methods=['POST'])
def classify_query():
    data = request.json
    query = data.get('query', '')

    if not query:
        return jsonify({"error": "Empty query"}), 400

    # 预测并提取主要标签
    labels, probabilities = classifier_model.predict(query, k=1)
    label = labels[0].replace('__label__', '')

    return jsonify({
        "query": query,
        "class": label,
        "probability": float(probabilities[0])
    })

def mean_pooling(model_output, attention_mask):
    """平均池化生成句子向量"""
    token_embeddings = model_output[0]
    input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
    return torch.sum(token_embeddings * input_mask_expanded, 1) / torch.clamp(input_mask_expanded.sum(1), min=1e-9)

@app.route('/vectorize', methods=['POST'])
def vectorize():
    """向量化API端点"""
    try:
        # 获取输入数据
        data = request.get_json()
        sentences = data.get('sentences', [])

        if not sentences:
            return jsonify({"error": "未提供句子"}), 400

        # 编码输入
        encoded_input = tokenizer(
            sentences,
            padding=True,
            truncation=True,
            max_length=128,
            return_tensors='pt'
        )

        # 计算向量
        with torch.no_grad():
            model_output = vector_model(**encoded_input)

        # 生成句子向量
        sentence_embeddings = mean_pooling(
            model_output,
            encoded_input['attention_mask']
        )

        # L2标准化
        sentence_embeddings = torch.nn.functional.normalize(
            sentence_embeddings,
            p=2,
            dim=1
        )

        # 转换为Python列表
        vectors = sentence_embeddings.numpy().tolist()

        return jsonify({
            "vectors": vectors,
            "dimension": len(vectors[0]) if vectors else 0,
            "count": len(vectors)
        })

    except Exception as e:
        app.logger.error(f"处理错误: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/health', methods=['GET'])
def health_check():
    """健康检查端点"""
    return jsonify({"status": "healthy", "models_loaded": True})

if __name__ == '__main__':
    load_vector_model()
    app.run(host='0.0.0.0', port=5000, debug=True)
