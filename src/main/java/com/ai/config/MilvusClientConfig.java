package com.ai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class MilvusClientConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    private MilvusServiceClient milvusServiceClient;

    // 使用构造函数注入并初始化客户端
    @Autowired
    public MilvusClientConfig(@Value("${milvus.host}") String host,
                              @Value("${milvus.port}") int port) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build();
        milvusServiceClient = new MilvusServiceClient(connectParam);
    }

}
