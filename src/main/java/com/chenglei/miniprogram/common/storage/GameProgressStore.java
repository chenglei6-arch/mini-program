package com.chenglei.miniprogram.common.storage;

import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 游戏进度的持久化：剧情、拼图等状态机型游戏把完整状态序列化为 JSON
 * 存入 game_progress，用 game_event 做事件幂等去重。
 */
@Service
public class GameProgressStore {

    private final GameProgressMapper mapper;

    public GameProgressStore(GameProgressMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取用户在某游戏下的状态 JSON；无记录返回 null。 */
    public String loadState(String userId, String gameId) {
        Map<String, Object> row = mapper.selectProgress(userId(gameId, userId), gameId);
        return row == null ? null : (String) row.get("progressJson");
    }

    /** 读取通关标记（game_progress.completed 列），避免消费方解析状态 JSON。 */
    public boolean isCompleted(String userId, String gameId) {
        Map<String, Object> row = mapper.selectProgress(userId(gameId, userId), gameId);
        return row != null && ((Number) row.get("completed")).intValue() == 1;
    }

    /**
     * 加锁读取状态（FOR UPDATE 行锁），无记录时插入初始行并返回其 JSON。
     * 用于"读取-修改-写回"的事件处理路径，保证同一用户的并发事件串行化。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String loadStateForUpdate(String userId, String gameId) {
        long id = userId(gameId, userId);
        Map<String, Object> row = mapper.selectProgressForUpdate(id, gameId);
        if (row == null) {
            try {
                mapper.insertProgress(id, gameId, "{}", false);
            } catch (DuplicateKeyException e) {
                // 并发首事件：行已由其他事务插入，继续持锁读取。
            }
            row = mapper.selectProgressForUpdate(id, gameId);
        }
        return (String) row.get("progressJson");
    }

    /** 写回状态 JSON。completed 表示该游戏是否已达成通关条件。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void saveState(String userId, String gameId, String stateJson, boolean completed) {
        long id = userId(gameId, userId);
        if (mapper.updateProgress(id, gameId, stateJson, completed) == 0) {
            try {
                mapper.insertProgress(id, gameId, stateJson, completed);
            } catch (DuplicateKeyException e) {
                mapper.updateProgress(id, gameId, stateJson, completed);
            }
        }
    }

    /** 删除用户在某游戏下的进度（重开一局）。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteState(String userId, String gameId) {
        long id = userId(gameId, userId);
        mapper.deleteProgress(id, gameId);
        mapper.deleteEvents(id, gameId);
    }

    /** 读取已记录事件的 payload JSON；事件不存在返回 null。 */
    public String findEventPayload(String userId, String gameId, String eventKey) {
        Map<String, Object> row = mapper.selectEvent(userId(gameId, userId), gameId, eventKey);
        return row == null ? null : (String) row.get("payloadJson");
    }

    /**
     * 记录事件（幂等键唯一）。事件已存在时返回 false，不覆盖原记录。
     * eventKey 为空时不记录，直接返回 true。
     */
    public boolean recordEvent(String userId, String gameId, String eventKey, String eventType, String payloadJson) {
        if (eventKey == null || eventKey.isBlank()) return true;
        try {
            mapper.insertEvent(userId(gameId, userId), gameId, eventKey, eventType, payloadJson);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private long userId(String gameId, String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游戏进度不存在（用户未初始化）：" + gameId);
        }
    }
}
