package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.common.storage.GameProgressStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 无独立服务实现的通用游戏进度（当前仅 paper-cutting 剪纸完成事件）。
 * 拥有 progress_json 里 {"completed":N} 这一最简状态格式的读写。
 */
@Service
public class GameProgressService {

    private static final String GAME_ID = "paper-cutting";

    private final GameProgressStore store;
    private final ObjectMapper objectMapper;

    public GameProgressService(GameProgressStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> progress(String userId, String gameId) {
        ensureSupported(gameId);
        int completed = completedFromState(store.loadState(userId, gameId));
        return Map.of("gameId", gameId, "completed", completed, "total", 1,
            "finished", completed > 0, "version", 1, "updatedAt", Instant.now());
    }

    /** completed/unlocked 事件统一记为通关。 */
    @Transactional
    public Map<String, Object> recordEvent(String userId, String gameId, String eventType) {
        ensureSupported(gameId);
        int completed = completedFromState(store.loadStateForUpdate(userId, gameId));
        if ("completed".equals(eventType) || "unlocked".equals(eventType)) {
            completed = 1;
            store.saveState(userId, gameId, stateJson(Map.of("completed", completed)), true);
        }
        return Map.of("accepted", true, "duplicated", false, "gameId", gameId,
            "progress", Map.of("gameId", gameId, "completed", completed, "total", 1,
                "finished", completed > 0, "version", 1, "updatedAt", Instant.now()));
    }

    private void ensureSupported(String gameId) {
        if (!GAME_ID.equals(gameId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游戏不存在");
        }
    }

    private int completedFromState(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) return 0;
        try {
            return objectMapper.readTree(stateJson).path("completed").asInt(0);
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    private String stateJson(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("游戏状态序列化失败", e);
        }
    }
}
