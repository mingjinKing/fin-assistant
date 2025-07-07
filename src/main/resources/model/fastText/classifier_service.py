# classifier_service.py
from flask import Flask, request, jsonify
import fasttext

app = Flask(__name__)
model = fasttext.load_model("query_classifier.bin")

@app.route('/classify', methods=['POST'])
def classify_query():
    data = request.json
    query = data.get('query', '')

    if not query:
        return jsonify({"error": "Empty query"}), 400

    # 预测并提取主要标签
    labels, probabilities = model.predict(query, k=1)
    label = labels[0].replace('__label__', '')

    return jsonify({
        "query": query,
        "class": label,
        "probability": float(probabilities[0])
    })

if __name__ == '__main__':
    app.run(port=5000, host='0.0.0.0')