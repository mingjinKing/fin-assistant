from sentence_transformers import SentenceTransformer

# 使用在线模型名称加载
model = SentenceTransformer("shibing624/text2vec-base-chinese")
print("Model loaded successfully!")