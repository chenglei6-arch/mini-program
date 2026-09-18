package com.chenglei.miniprogram.story;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads player-visible story prose from a versioned classpath content file. */
@Component
public class StoryContentCatalog {

    private final Map<String, List<String>> scenes;

    public StoryContentCatalog(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("story/story-content.json").getInputStream()) {
            Map<String, List<String>> loaded = objectMapper.readValue(input, new TypeReference<>() { });
            scenes = Collections.unmodifiableMap(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("故事内容资源加载失败", exception);
        }
    }

    public List<String> paragraphs(String key) {
        return scenes.getOrDefault(key, List.of());
    }
}
