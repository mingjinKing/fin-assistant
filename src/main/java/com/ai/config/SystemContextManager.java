package com.ai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SystemContextManager {



    public final ConcurrentHashMap<String, String> CONTEXT_PARAMS = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, String> getContextParams() {
        return CONTEXT_PARAMS;
    }

    public void setContextParams(String key, String value) {
        CONTEXT_PARAMS.put(key, value);
    }
}
