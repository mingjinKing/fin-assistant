package com.ai.controller;

import io.github.pigmesh.ai.deepseek.config.DeepSeekProperties;
import io.github.pigmesh.ai.deepseek.core.DeepSeekClient;
import io.github.pigmesh.ai.deepseek.core.Json;
import io.github.pigmesh.ai.deepseek.core.SyncOrAsyncOrStreaming;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionChoice;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionRequest;
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse;
import io.github.pigmesh.ai.deepseek.core.models.ModelsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ollama")
@RequiredArgsConstructor
public class OllamaLocalController {

    @Resource
    private DeepSeekClient deepSeekClient;

    @Resource
    private DeepSeekProperties deepSeekProperties;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatCompletionResponse> chat(String prompt) {
        return deepSeekClient.chatFluxCompletion(prompt);
    }

    // 同步
    @GetMapping(value = "/sync/chat")
    public ChatCompletionResponse syncChat(String prompt) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                // 根据渠道模型名称动态修改这个参数
                .model(deepSeekProperties.getModel()).addUserMessage(prompt).build();

        return deepSeekClient.chatCompletion(request).execute();
    }

    // 异步
    @GetMapping(value = "/async/chat")
    public void asyncChat(String prompt) {
        ChatCompletionRequest request = ChatCompletionRequest.builder().model(deepSeekProperties.getModel())
                .addUserMessage(prompt).build();
        SyncOrAsyncOrStreaming<ChatCompletionResponse> asyncOrStreaming = deepSeekClient.chatCompletion(request);
        asyncOrStreaming.onResponse((responseContent) -> {
            log.info("处理返回数据：{}", Json.toJson(responseContent));
        }, exception -> {
            log.info("处理异常数据：{}", exception.getMessage());
        });
    }

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelsResponse models() {
        return deepSeekClient.models();
    }

    public final static HashMap<String, String> cache = new HashMap<>();

    @GetMapping(value = "/chat/advanced", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatCompletionResponse> chatAdvanced(String prompt, String cacheCode) {
        log.info("cacheCode {}", cacheCode);

        ChatCompletionRequest request = ChatCompletionRequest.builder().model(deepSeekProperties.getModel())
                .addUserMessage(prompt).addAssistantMessage(elt.apply(cache.getOrDefault(cacheCode, "")))
                .addSystemMessage("你是一个专业的助手").maxCompletionTokens(5000).build();
        log.info("request {}", Json.toJson(request));
        // 只保留上一次回答内容
        cache.remove(cacheCode);
        return deepSeekClient.chatFluxCompletion(request).doOnNext(i -> {
            String content = choicesProcess.apply(i.choices());
            // 其他ELT流程
            cache.merge(cacheCode, content, String::concat);
        }).doOnError(e -> log.error("/chat/advanced error:{}", e.getMessage()));
    }

    Function<List<ChatCompletionChoice>, String> choicesProcess = list -> list.stream().map(e -> e.delta().content())
            .collect(Collectors.joining());

    Function<String, String> elt = s -> s.replaceAll("<think>[\\s\\S]*?</think>", "").replaceAll("\n", "");

}
