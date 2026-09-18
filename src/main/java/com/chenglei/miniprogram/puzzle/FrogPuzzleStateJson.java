package com.chenglei.miniprogram.puzzle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 拼图进度的 JSON 编解码组件：PuzzleProgress 与 game_progress.progress_json、
 * game_event.payload_json 之间的唯一转换点。
 *
 * BadgeService 通过 {@link #completedFrogCount(String)} 读取完成蛙数，
 * 不直接依赖 PuzzleProgress 的字段名。JSON 损坏时直接抛错。
 */
@Component
public class FrogPuzzleStateJson {

    private final ObjectMapper objectMapper;

    public FrogPuzzleStateJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 还没有进度（null）、初始占位（"{}"）时表示一只蛙都没拼完。 */
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
            return objectMapper.writeValueAsString(Map.of("frogId", frogId));
        } catch (Exception e) {
            throw new IllegalStateException("拼图事件序列化失败", e);
        }
    }

    /** 已完成拼贴的蛙数，供徽章判定使用；还没有进度时按 0 处理。 */
    public int completedFrogCount(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) return 0;
        try {
            return objectMapper.readTree(stateJson).path("completedFrogs").size();
        } catch (Exception e) {
            throw new IllegalStateException("拼图进度读取失败", e);
        }
    }
}
