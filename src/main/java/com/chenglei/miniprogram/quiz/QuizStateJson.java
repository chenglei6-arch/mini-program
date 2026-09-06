package com.chenglei.miniprogram.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 闯关进度的 JSON 编解码组件：completed_levels_json 列与已通关关卡 id 列表
 * 之间的唯一转换点。BadgeService 通过 {@link #completedLevelIds(String)} 读取，
 * 不直接依赖 JSON 结构。
 */
@Component
public class QuizStateJson {

    private final ObjectMapper objectMapper;

    public QuizStateJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(List<String> completedLevelIds) {
        try {
            return objectMapper.writeValueAsString(completedLevelIds);
        } catch (Exception e) {
            throw new IllegalStateException("问答进度序列化失败", e);
        }
    }

    /** 进度不存在或格式异常时按空列表处理。 */
    public List<String> completedLevelIds(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return List.of();
        try {
            List<String> levels = new ArrayList<>();
            objectMapper.readTree(json).forEach(item -> levels.add(item.asText()));
            return levels;
        } catch (Exception e) {
            return List.of();
        }
    }
}
