package com.ai.service;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.DefaultVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChineseEmbeddingService {

    public static final String url = "http://localhost:5000/vectorize";


    public List<Float> getEmbedding(String text) {
        // 构造请求参数（使用 Map 自动转 JSON）
        Map<String, Object> paramMap = new HashMap<>();
        List<String> sentences = new ArrayList<>();
        sentences.add(text);
        paramMap.put("sentences", sentences);

        String jsonBody = JSONUtil.toJsonStr(paramMap);

        // 发送 POST 请求
        String response = HttpUtil.post(url, jsonBody);

        // 解析响应
        JSONObject jsonObject = JSONUtil.parseObj(response);

        if (!jsonObject.containsKey("vectors")) {
            throw new RuntimeException("未获取到有效的向量数据");
        }

        JSONArray vectorsArray = jsonObject.getJSONArray("vectors");

        if (vectorsArray.isEmpty()) {
            throw new RuntimeException("返回向量为空");
        }

        // 获取第一个句子的向量
        JSONArray vector = vectorsArray.getJSONArray(0);
        List<Float> embedding = new ArrayList<>();

        for (int i = 0; i < vector.size(); i++) {
            embedding.add(vector.getDouble(i).floatValue());
        }

        return embedding;
    }

    public List<List<Float>> batchEmbed(List<String> texts) {
        // 构造请求参数
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("sentences", texts);

        String jsonBody = JSONUtil.toJsonStr(paramMap);

        String response = HttpUtil.post(url, jsonBody);

        JSONObject jsonObject = JSONUtil.parseObj(response);

        if (!jsonObject.containsKey("vectors")) {
            throw new RuntimeException("未获取到有效的向量数据");
        }

        JSONArray vectorsArray = jsonObject.getJSONArray("vectors");

        List<List<Float>> embeddings = new ArrayList<>();

        for (int i = 0; i < vectorsArray.size(); i++) {
            JSONArray vector = vectorsArray.getJSONArray(i);
            List<Float> embedding = new ArrayList<>();
            for (int j = 0; j < vector.size(); j++) {
                embedding.add(vector.getDouble(j).floatValue());
            }
            embeddings.add(embedding);
        }

        return embeddings;
    }

}
