package com.ai.config;

import com.ai.entity.SqlTemplate;
import com.ai.util.SqlTemplateExtractor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.*;

@Log4j2
@Service
public class TemplateVersionManager {
    private final Map<String, List<SqlTemplate>> templateVersions = new HashMap<>();

    public void addTemplate(SqlTemplate template) {
        String baseHash = template.getNormalizedStructure();
        templateVersions.computeIfAbsent(baseHash, k -> new ArrayList<>()).add(template);
    }

    public SqlTemplate getLatestVersion(String baseHash) {
        List<SqlTemplate> versions = templateVersions.get(baseHash);
        if (versions == null || versions.isEmpty()) return null;

        // 按时间戳排序（实际应用中应记录时间戳）
        versions.sort(Comparator.comparing(SqlTemplate::getHash));
        return versions.get(versions.size() - 1);
    }

    public SqlTemplate getBestMatch(SqlTemplate newTemplate) {
        for (List<SqlTemplate> versions : templateVersions.values()) {
            for (SqlTemplate template : versions) {
                if (SqlTemplateExtractor.calculateSimilarity(template, newTemplate) > 0.95) {
                    return template;
                }
            }
        }
        return null;
    }
}