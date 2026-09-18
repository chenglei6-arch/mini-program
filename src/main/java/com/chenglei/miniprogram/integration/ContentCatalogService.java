package com.chenglei.miniprogram.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 首页、角色与公益内容的目录读取。
 * 角色设计说明是静态内容，随包放在 classpath 内容文件里，不进数据库。
 */
@Service
public class ContentCatalogService {

    private final ContentCatalogMapper mapper;
    private final boolean includeTestData;
    private final Map<String, List<Map<String, Object>>> frogSections;

    public ContentCatalogService(ContentCatalogMapper mapper, ObjectMapper objectMapper,
        @Value("${app.content.include-test-data:false}") boolean includeTestData) {
        this.mapper = mapper;
        this.includeTestData = includeTestData;
        this.frogSections = loadFrogSections(objectMapper);
    }

    public List<Map<String, Object>> frogs() {
        return mapper.selectFrogs(includeTestData);
    }

    public Map<String, Object> frog(String id) {
        Map<String, Object> frog = mapper.selectFrog(id, includeTestData);
        if (frog == null) return null;
        frog.put("sections", frogSections.get(id));
        return frog;
    }

    public List<Map<String, Object>> games() {
        return mapper.selectGames(includeTestData);
    }

    public List<Map<String, Object>> guardianFrogs() {
        return mapper.selectFrogs(false);
    }

    public Map<String, String> homeConfig() {
        return mapper.selectHomeConfig();
    }

    public Map<String, String> welfare() {
        return mapper.selectWelfare();
    }

    public List<ContentCatalogMapper.WelfareReportRow> welfareReports() {
        return mapper.selectWelfareReports();
    }

    private static Map<String, List<Map<String, Object>>> loadFrogSections(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("content/frog-details.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            Map<String, List<Map<String, Object>>> sections = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> sections.put(entry.getKey(), objectMapper.convertValue(
                entry.getValue().path("sections"), new TypeReference<List<Map<String, Object>>>() { })));
            return Map.copyOf(sections);
        } catch (Exception error) {
            throw new IllegalStateException("林蛙设计说明加载失败", error);
        }
    }
}
