package com.chenglei.miniprogram.common.storage;

import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DbGameProgressStore implements GameProgressStore {

    private final GameProgressMapper mapper;

    public DbGameProgressStore(GameProgressMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String loadState(String userId, String gameId) {
        Map<String, Object> row = mapper.selectProgress(userId(gameId, userId), gameId);
        return row == null ? null : (String) valueOf(row, "progressJson");
    }

    @Override
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
        return row == null ? null : (String) valueOf(row, "progressJson");
    }

    @Override
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

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteState(String userId, String gameId) {
        long id = userId(gameId, userId);
        mapper.deleteProgress(id, gameId);
        mapper.deleteEvents(id, gameId);
    }

    @Override
    public String findEventPayload(String userId, String gameId, String eventKey) {
        Map<String, Object> row = mapper.selectEvent(userId(gameId, userId), gameId, eventKey);
        return row == null ? null : (String) valueOf(row, "payloadJson");
    }

    @Override
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

    /** H2（DATABASE_TO_LOWER）会把列别名转小写，MySQL 保留大小写，因此按键大小写不敏感取值。 */
    private static Object valueOf(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value != null || values.containsKey(key)) return value;
        return values.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(key))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
}
