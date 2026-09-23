package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.file.OssStorageService;
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
 * assetUrl 在库里只存 /assets/... 相对路径，出接口前统一合成 OSS 完整 URL。
 */
@Service
public class ContentCatalogService {

    private final ContentCatalogMapper mapper;
    private final OssStorageService storage;
    private final boolean includeTestData;
    private final Map<String, List<Map<String, Object>>> frogSections;

    public ContentCatalogService(ContentCatalogMapper mapper, OssStorageService storage, ObjectMapper objectMapper,
        @Value("${app.content.include-test-data:false}") boolean includeTestData) {
        this.mapper = mapper;
        this.storage = storage;
        this.includeTestData = includeTestData;
        this.frogSections = loadFrogSections(objectMapper);
    }

    public List<Map<String, Object>> frogs() {
        return withPublicAssetUrls(mapper.selectFrogs(includeTestData));
    }

    public Map<String, Object> frog(String id) {
        Map<String, Object> frog = mapper.selectFrog(id, includeTestData);
        if (frog == null) return null;
        frog.computeIfPresent("assetUrl", (key, value) -> storage.publicUrl(String.valueOf(value)));
        frog.put("sections", frogSections.get(id));
        return frog;
    }

    public List<Map<String, Object>> games() {
        return mapper.selectGames(includeTestData);
    }

    public List<Map<String, Object>> guardianFrogs() {
        return withPublicAssetUrls(mapper.selectFrogs(false));
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

    private List<Map<String, Object>> withPublicAssetUrls(List<Map<String, Object>> frogs) {
        frogs.forEach(frog -> frog.computeIfPresent("assetUrl",
            (key, value) -> storage.publicUrl(String.valueOf(value))));
        return frogs;
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
