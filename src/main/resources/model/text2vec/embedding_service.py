from flask import Flask, request, jsonify
from transformers import AutoModel, AutoTokenizer
import torch
import numpy as np
import logging

# 初始化Flask应用
app = Flask(__name__)
app.logger.setLevel(logging.INFO)

# 全局加载模型
MODEL_NAME = "/Users/Wangmj/project/src/main/resources/models/text2vec-base-chinese/"
tokenizer = None
model = None

def load_model():
    """加载模型和分词器"""
    global tokenizer, model
    app.logger.info("开始加载模型...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME)
    app.logger.info("模型加载完成!")

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
            model_output = model(**encoded_input)

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
    return jsonify({"status": "healthy", "model": MODEL_NAME})

if __name__ == '__main__':
    load_model()
    app.run(host='0.0.0.0', port=5000, debug=True)