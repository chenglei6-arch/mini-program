package com.chenglei.miniprogram.common.storage;

/**
 * 游戏进度的持久化抽象：剧情、拼图等状态机型游戏把完整状态序列化为 JSON
 * 存入 game_progress，用 game_event 做事件幂等去重。
 * db 实现见 {@link DbGameProgressStore}；无数据库联调模式见 {@link InMemoryGameProgressStore}。
 */
public interface GameProgressStore {

    /** 读取用户在某游戏下的状态 JSON；无记录返回 null。 */
    String loadState(String userId, String gameId);

    /**
     * 加锁读取状态（db 实现为 FOR UPDATE 行锁），无记录时插入初始行并返回其 JSON。
     * 用于"读取-修改-写回"的事件处理路径，保证同一用户的并发事件串行化。
     */
    String loadStateForUpdate(String userId, String gameId);

    /** 写回状态 JSON。completed 表示该游戏是否已达成通关条件。 */
    void saveState(String userId, String gameId, String stateJson, boolean completed);

    /** 删除用户在某游戏下的进度（重开一局）。 */
    void deleteState(String userId, String gameId);

    /** 读取已记录事件的 payload JSON；事件不存在返回 null。 */
    String findEventPayload(String userId, String gameId, String eventKey);

    /**
     * 记录事件（幂等键唯一）。事件已存在时返回 false，不覆盖原记录。
     * eventKey 为空时不做记录，直接返回 true。
     */
    boolean recordEvent(String userId, String gameId, String eventKey, String eventType, String payloadJson);
}
