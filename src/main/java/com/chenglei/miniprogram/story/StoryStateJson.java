package com.chenglei.miniprogram.story;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 剧情状态的 JSON 编解码组件：StoryState 与 game_progress.progress_json、
 * game_event.payload_json 之间的唯一转换点。
 *
 * BadgeService 等外部消费方也通过本组件解析进度 JSON，
 * 避免把 StoryState 的字段名耦合扩散到其他模块。
 */
@Component
public class StoryStateJson {

    private final ObjectMapper objectMapper;

    public StoryStateJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(StoryGameService.StoryState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("故事状态序列化失败", e);
        }
    }

    public StoryGameService.StoryState read(String json) {
        try {
            return objectMapper.readValue(json, StoryGameService.StoryState.class);
        } catch (Exception e) {
            throw new IllegalStateException("故事状态反序列化失败", e);
        }
    }

    /** 事件 payload：{"choiceId":"A"}。 */
    public String choicePayload(String choiceId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("choiceId", choiceId));
        } catch (Exception e) {
            throw new IllegalStateException("故事事件序列化失败", e);
        }
    }

    public String choiceOf(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return null;
        try {
            return objectMapper.readTree(payloadJson).path("choiceId").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 已完成路线数，供徽章判定使用；进度不存在或格式异常时按 0 处理。 */
    public int completedRouteCount(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) return 0;
        try {
            return objectMapper.readTree(stateJson).path("completedRoutes").size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 剧情是否已通关（到达结局），供徽章判定使用。 */
    public boolean finished(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) return false;
        try {
            return objectMapper.readTree(stateJson).path("finished").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }
}
