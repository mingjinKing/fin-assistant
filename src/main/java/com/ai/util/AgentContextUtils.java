package com.ai.util;

import java.util.HashMap;
import java.util.Map;

public class AgentContextUtils {

    public static final Map<String, String> CONTEXT_PARAMS = new HashMap<>();
    public static Map<String, String> getContextParams() {
        CONTEXT_PARAMS.put("{user_id}", "U1002"); // 实际从请求头获取
        return CONTEXT_PARAMS;
    }


}
