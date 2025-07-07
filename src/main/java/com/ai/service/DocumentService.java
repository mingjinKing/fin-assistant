package com.ai.service;

import com.ai.service.miluvs.MilvusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.file.Files;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentService {

    @Autowired
    private MilvusService milvusService;
    @Autowired
    private ChineseEmbeddingService embeddingService;

    public void processAndStoreDocument(String filePath) throws Exception {
        File file = ResourceUtils.getFile(filePath);
        String content = new String(Files.readAllBytes(file.toPath()));

        // 文本分割器
        List<String> chunks = splitText(content, 500);

        List<List<Float>> vectors = new ArrayList<>(embeddingService.batchEmbed(chunks));

        //milvusService.insertDocuments(chunks, vectors);
    }

    private List<String> splitText(String text, int maxChunkSize) {
        // 实现更智能的分块（如按句子分割）
        List<String> chunks = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.CHINESE);
        iterator.setText(text);

        int start = iterator.first();
        int end = iterator.next();

        StringBuilder currentChunk = new StringBuilder();
        while (end != BreakIterator.DONE) {
            String sentence = text.substring(start, end);
            if (currentChunk.length() + sentence.length() > maxChunkSize) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder(sentence);
            } else {
                currentChunk.append(sentence);
            }
            start = end;
            end = iterator.next();
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }
}
