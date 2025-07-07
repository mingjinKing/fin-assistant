package com.ai.util;

import reactor.core.publisher.Flux;

public class FluxUtils {

    public static Flux<?> string2Flux(String original){
        return Flux.generate(
                () -> 0,  // 初始化指针索引
                (index, sink) -> {
                    if (index >= original.length()) {
                        sink.complete();  // 数据已全部发送完成
                    } else {
                        // 计算当前块的结束位置（不超过字符串长度）
                        int endIndex = Math.min(index + 3, original.length());
                        // 截取3个字符的子串
                        sink.next(original.substring(index, endIndex));
                    }
                    return index + 3;  // 移动指针到下一个块起始位置
                }
        );
    }

}
