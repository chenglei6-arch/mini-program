package com.chenglei.miniprogram.puzzle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 拼图进度的 JSON 编解码组件：PuzzleProgress 与 game_progress.progress_json、
 * game_event.payload_json 之间的唯一转换点。
 *
 * BadgeService 通过 {@link #completedFrogCount(String)} 读取完成蛙数，
 * 不直接依赖 PuzzleProgress 的字段名。
 */
@Component
public class FrogPuzzleStateJson {

    private final ObjectMapper objectMapper;

    public FrogPuzzleStateJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 进度不存在（null）、空占位（"{}"）或格式异常时按空进度处理。 */
    public FrogPuzzleService.PuzzleProgress read(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return new FrogPuzzleService.PuzzleProgress();
        try {
            return objectMapper.readValue(json, FrogPuzzleService.PuzzleProgress.class);
        } catch (Exception e) {
            throw new IllegalStateException("拼图进度反序列化失败", e);
        }
    }

    public String write(FrogPuzzleService.PuzzleProgress progress) {
        try {
            return objectMapper.writeValueAsString(progress);
        } catch (Exception e) {
            throw new IllegalStateException("拼图进度序列化失败", e);
        }
    }

    /** 事件 payload：{"frogId":"forest"}。 */
    public String frogPayload(String frogId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("frogId", frogId));
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 已完成拼贴的蛙数，供徽章判定使用；进度不存在时按 0 处理。 */
    public int completedFrogCount(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) return 0;
        try {
            return objectMapper.readTree(stateJson).path("completedFrogs").size();
        } catch (Exception e) {
            return 0;
        }
    }
}
