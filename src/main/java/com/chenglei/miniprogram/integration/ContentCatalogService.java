package com.chenglei.miniprogram.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed catalog for all player-visible home and frog content. */
@Service
public class ContentCatalogService {

    private final ContentCatalogMapper mapper;
    private final ObjectMapper objectMapper;
    private final boolean includeTestData;

    public ContentCatalogService(ContentCatalogMapper mapper, ObjectMapper objectMapper,
        @Value("${app.content.include-test-data:true}") boolean includeTestData) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.includeTestData = includeTestData;
    }

    @PostConstruct
    @Transactional
    void seedIfEmpty() {
        if (mapper.countFrogs() != 0) return;
        try (InputStream seedStream = new ClassPathResource("content-seed.json").getInputStream();
             InputStream detailsStream = new ClassPathResource("static/assets/frogs/frog-details.json").getInputStream()) {
            JsonNode seed = objectMapper.readTree(seedStream);
            Map<String, Map<String, Object>> details = objectMapper.readValue(detailsStream,
                new TypeReference<Map<String, Map<String, Object>>>() { });
            int order = 0;
            for (JsonNode frog : seed.path("frogs")) {
                String frogId = frog.path("id").asText();
                mapper.insertFrog(frogId, frog.path("name").asText(), frog.path("shortName").asText(),
                    frog.path("pattern").asText(), frog.path("blessing").asText(), frog.path("assetUrl").asText(),
                    frog.path("sourceUrl").asText(), order++);
                Map<String, Object> frogDetail = details.get(frogId);
                if (frogDetail == null) continue;
                Object sectionValue = frogDetail.get("sections");
                List<?> sections = sectionValue instanceof List<?> list ? list : List.of();
                int sectionOrder = 0;
                for (Object sectionObject : sections) {
                    Map<?, ?> section = (Map<?, ?>) sectionObject;
                    int currentSectionOrder = sectionOrder++;
                    mapper.insertSection(frogId, String.valueOf(section.get("heading")), currentSectionOrder);
                    Long sectionId = mapper.selectSectionId(frogId, currentSectionOrder);
                    Object paragraphValue = section.get("paragraphs");
                    List<?> paragraphs = paragraphValue instanceof List<?> list ? list : List.of();
                    int paragraphOrder = 0;
                    for (Object paragraph : paragraphs) {
                        mapper.insertParagraph(sectionId, String.valueOf(paragraph), paragraphOrder++);
                    }
                }
            }
            int gameOrder = 0;
            for (JsonNode game : seed.path("games")) {
                mapper.insertGame(game.path("id").asText(), game.path("title").asText(), game.path("subtitle").asText(),
                    game.path("path").asText(), gameOrder++);
            }
            int patternOrder = 0;
            for (JsonNode pattern : seed.path("patterns")) {
                mapper.insertPattern(pattern.asText(), patternOrder++);
            }
            int badgeOrder = 0;
            for (JsonNode badge : seed.path("badges")) {
                mapper.insertBadge(badge.path("id").asText(), badge.path("name").asText(), badge.path("level").asText(), badgeOrder++);
            }
            JsonNode home = seed.path("home");
            mapper.insertHomeConfig(home.path("brand").asText(), home.path("slogan").asText(),
                home.path("fundAmount").asText(), home.path("fundUpdatedAt").asText());
            int activityOrder = 0;
            for (JsonNode activity : seed.path("activities")) {
                mapper.insertActivity(activity.asText(), activityOrder++);
            }
            JsonNode welfare = seed.path("welfare");
            mapper.insertWelfare(welfare.path("fundAmount").asText(), welfare.path("updatedAt").asText());
        } catch (Exception error) {
            throw new IllegalStateException("内容种子写入数据库失败", error);
        }
    }

    public List<Map<String, Object>> frogs() {
        List<Map<String, Object>> frogs = mapper.selectFrogs(includeTestData);
        for (int index = 0; index < frogs.size(); index++) {
            normalizeKeys(frogs.get(index), "shortName", "assetUrl", "sourceUrl");
            frogs.get(index).put("unlocked", index == 0);
        }
        return frogs;
    }

    public Map<String, Object> frog(String id) {
        Map<String, Object> frog = mapper.selectFrog(id, includeTestData);
        if (frog == null) return null;
        normalizeKeys(frog, "shortName", "assetUrl", "sourceUrl");
        frog.put("unlocked", "forest".equals(id));
        List<Map<String, Object>> sections = mapper.selectSections(id);
        for (Map<String, Object> section : sections) {
            Number sectionId = (Number) section.get("id");
            section.put("paragraphs", mapper.selectParagraphs(sectionId.longValue()));
            section.remove("id");
        }
        frog.put("sections", sections);
        return frog;
    }

    public List<Map<String, Object>> games() { return mapper.selectGames(includeTestData); }

    public List<String> patterns() { return mapper.selectPatterns(includeTestData); }

    public List<Map<String, Object>> badges() {
        List<Map<String, Object>> badges = mapper.selectBadges(includeTestData);
        for (int index = 0; index < badges.size(); index++) badges.get(index).put("unlocked", index == 0);
        return badges;
    }

    public Map<String, String> homeConfig() {
        Map<String, String> values = mapper.selectHomeConfig();
        normalizeStringKeys(values, "fundAmount", "fundUpdateAt");
        return values;
    }

    public List<String> activities() { return mapper.selectActivities(includeTestData); }

    public Map<String, String> welfare() {
        Map<String, String> values = mapper.selectWelfare();
        normalizeStringKeys(values, "fundAmount", "updatedAt");
        return values;
    }

    private static void normalizeKeys(Map<String, Object> values, String... expectedKeys) {
        for (String expectedKey : expectedKeys) {
            if (values.containsKey(expectedKey)) continue;
            for (String actualKey : List.copyOf(values.keySet())) {
                if (actualKey.equalsIgnoreCase(expectedKey)) {
                    values.put(expectedKey, values.remove(actualKey));
                    break;
                }
            }
        }
    }

    private static void normalizeStringKeys(Map<String, String> values, String... expectedKeys) {
        for (String expectedKey : expectedKeys) {
            if (values.containsKey(expectedKey)) continue;
            for (String actualKey : List.copyOf(values.keySet())) {
                if (actualKey.equalsIgnoreCase(expectedKey)) {
                    values.put(expectedKey, values.remove(actualKey));
                    break;
                }
            }
        }
    }
}
