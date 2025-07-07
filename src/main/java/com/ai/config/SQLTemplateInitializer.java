package com.ai.config;

import com.ai.entity.SqlTemplateEntity;
import com.ai.mapper.SqlTemplateMapper;
import com.ai.service.bi.AdvancedSqlTemplateEngine;
import com.ai.service.miluvs.SQLFingerPrintService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SQLTemplateInitializer {

    @Autowired
    private AdvancedSqlTemplateEngine advancedSqlTemplateEngine;
    @Autowired
    private SqlTemplateMapper sqlTemplateMapper;

    private final Map<String, String> sqlTemplateMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initCommonTemplates(){
        List<SqlTemplateEntity> sqlTemplateEntities = sqlTemplateMapper.selectList(new QueryWrapper<>());
        advancedSqlTemplateEngine.addNewMappingBatch(sqlTemplateEntities);
    }

}
