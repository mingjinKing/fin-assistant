package com.ai.util;

import cn.hutool.core.util.ObjUtil;
import com.ai.config.SpringContextUtil;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionModel;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionRequest;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import io.github.pigmesh.ai.deepseek.core.chat.Delta;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class DeepSeekClientUtils {

    public static String getDeepSeekResponseContent(String prompt) {
        StringBuilder result = new StringBuilder();
        DeepSeekClient deepSeekClient = (DeepSeekClient) SpringContextUtil.getBean("deepSeekClient");

        // 使用 CountDownLatch 进行同步控制
        CountDownLatch latch = new CountDownLatch(1);

        Flux<ChatCompletionResponse> response = deepSeekClient.chatFluxCompletion(prompt)
                .cast(ChatCompletionResponse.class)
                .doOnNext(chunk -> {
                    Delta delta = chunk.choices().get(0).delta();
                    String item = ObjUtil.isEmpty(delta) ? "" : delta.content();
                    if (StringUtils.isNotEmpty(item)) {
                        result.append(item);
                    }
                })
                .doFinally(signal -> {
                    // 无论完成还是错误都释放锁
                    latch.countDown();
                });

        // 订阅并启动流处理
        response.subscribe();

        try {
            // 阻塞等待直到流处理完成
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待模型响应时被中断", e);
        }

        return result.toString();
    }

    public static String getDeepSeekContentWithChatModel(String prompt) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                // 根据渠道模型名称动态修改这个参数
                .model(ChatCompletionModel.DEEPSEEK_CHAT)
                .temperature(0.0)
                .addUserMessage(prompt).build();
        DeepSeekClient deepSeekClient = (DeepSeekClient) SpringContextUtil.getBean("deepSeekClient");
        return deepSeekClient.chatCompletion(request).execute().choices().get(0).message().content();
    };

}
