package com.chenglei.miniprogram.common.storage;

import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * game_progress / game_event 通用进度表访问：剧情、拼图等状态机型游戏共用。
 * selectProgressForUpdate 使用行锁，配合 GameProgressStore.loadStateForUpdate
 * 保证同一用户的并发事件串行处理。
 *
 * completed 用 CASE 输出 0/1 数字：不同驱动对 TINYINT 的返回类型不一致，
 * 这里统一在 SQL 层定死类型，Java 侧不再做类型兜底。
 */
@Mapper
public interface GameProgressMapper {

    @Select("SELECT progress_json AS \"progressJson\", "
        + "CASE WHEN completed = 1 THEN 1 ELSE 0 END AS \"completed\" FROM game_progress "
        + "WHERE user_id=#{userId} AND game_id=#{gameId} FOR UPDATE")
    Map<String, Object> selectProgressForUpdate(@Param("userId") long userId,
        @Param("gameId") String gameId);

    @Select("SELECT progress_json AS \"progressJson\", "
        + "CASE WHEN completed = 1 THEN 1 ELSE 0 END AS \"completed\" FROM game_progress "
        + "WHERE user_id=#{userId} AND game_id=#{gameId}")
    Map<String, Object> selectProgress(@Param("userId") long userId, @Param("gameId") String gameId);

    @Insert("INSERT IGNORE INTO game_progress (user_id, game_id, progress_json, completed) "
        + "VALUES (#{userId}, #{gameId}, #{progressJson}, #{completed})")
    int insertProgress(@Param("userId") long userId, @Param("gameId") String gameId,
        @Param("progressJson") String progressJson, @Param("completed") boolean completed);

    @Update("UPDATE game_progress SET progress_json=#{progressJson}, "
        + "completed=#{completed}, version=version+1, updated_at=CURRENT_TIMESTAMP(3) "
        + "WHERE user_id=#{userId} AND game_id=#{gameId}")
    int updateProgress(@Param("userId") long userId, @Param("gameId") String gameId,
        @Param("progressJson") String progressJson, @Param("completed") boolean completed);

    @Delete("DELETE FROM game_progress WHERE user_id=#{userId} AND game_id=#{gameId}")
    int deleteProgress(@Param("userId") long userId, @Param("gameId") String gameId);

    @Select("SELECT payload_json AS \"payloadJson\" FROM game_event "
        + "WHERE user_id=#{userId} AND game_id=#{gameId} AND event_key=#{eventKey}")
    Map<String, Object> selectEvent(@Param("userId") long userId, @Param("gameId") String gameId,
        @Param("eventKey") String eventKey);

    @Insert("INSERT INTO game_event (user_id, game_id, event_key, event_type, payload_json) "
        + "VALUES (#{userId}, #{gameId}, #{eventKey}, #{eventType}, #{payloadJson})")
    int insertEvent(@Param("userId") long userId, @Param("gameId") String gameId,
        @Param("eventKey") String eventKey, @Param("eventType") String eventType,
        @Param("payloadJson") String payloadJson);

    @Delete("DELETE FROM game_event WHERE user_id=#{userId} AND game_id=#{gameId}")
    int deleteEvents(@Param("userId") long userId, @Param("gameId") String gameId);
}
