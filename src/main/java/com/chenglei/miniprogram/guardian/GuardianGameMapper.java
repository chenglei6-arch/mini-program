package com.chenglei.miniprogram.guardian;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GuardianGameMapper {

    @Select("SELECT game_date AS gameDate, draws_used AS drawsUsed, share_bonus_claimed AS shareBonusClaimed "
        + "FROM guardian_daily_state WHERE user_id=#{userId} AND game_date=#{gameDate} FOR UPDATE")
    Map<String, Object> selectDailyStateForUpdate(@Param("userId") String userId, @Param("gameDate") LocalDate gameDate);

    @Select("SELECT game_date AS gameDate, draws_used AS drawsUsed, share_bonus_claimed AS shareBonusClaimed "
        + "FROM guardian_daily_state WHERE user_id=#{userId} AND game_date=#{gameDate}")
    Map<String, Object> selectDailyState(@Param("userId") String userId, @Param("gameDate") LocalDate gameDate);

    @Insert("INSERT INTO guardian_daily_state (user_id, game_date, draws_used, share_bonus_claimed) "
        + "VALUES (#{userId}, #{gameDate}, 0, 0)")
    int insertDailyState(@Param("userId") String userId, @Param("gameDate") LocalDate gameDate);

    @Update("UPDATE guardian_daily_state SET draws_used=#{drawsUsed}, updated_at=CURRENT_TIMESTAMP(3) "
        + "WHERE user_id=#{userId} AND game_date=#{gameDate}")
    int updateDrawsUsed(@Param("userId") String userId, @Param("gameDate") LocalDate gameDate,
        @Param("drawsUsed") int drawsUsed);

    @Update("UPDATE guardian_daily_state SET share_bonus_claimed=1, updated_at=CURRENT_TIMESTAMP(3) "
        + "WHERE user_id=#{userId} AND game_date=#{gameDate}")
    int claimShareBonus(@Param("userId") String userId, @Param("gameDate") LocalDate gameDate);

    @Select("SELECT frog_id FROM guardian_collection WHERE user_id=#{userId} ORDER BY unlocked_at, frog_id")
    List<String> selectCollectedFrogIds(@Param("userId") String userId);

    @Insert("INSERT INTO guardian_collection (user_id, frog_id) VALUES (#{userId}, #{frogId})")
    int insertCollection(@Param("userId") String userId, @Param("frogId") String frogId);

    @Select("SELECT round_id AS roundId, frog_id AS frogId, options_json AS optionsJson, created_at AS createdAt "
        + "FROM guardian_round WHERE user_id=#{userId} FOR UPDATE")
    Map<String, Object> selectRoundForUpdate(@Param("userId") String userId);

    @Select("SELECT round_id AS roundId, frog_id AS frogId, options_json AS optionsJson, created_at AS createdAt "
        + "FROM guardian_round WHERE user_id=#{userId}")
    Map<String, Object> selectRound(@Param("userId") String userId);

    @Insert("INSERT INTO guardian_round (user_id, round_id, frog_id, options_json) "
        + "VALUES (#{userId}, #{roundId}, #{frogId}, #{optionsJson})")
    int insertRound(@Param("userId") String userId, @Param("roundId") String roundId, @Param("frogId") String frogId,
        @Param("optionsJson") String optionsJson);

    @Delete("DELETE FROM guardian_round WHERE user_id=#{userId}")
    int deleteRound(@Param("userId") String userId);

    @Select("SELECT event_type AS eventType, response_json AS responseJson FROM guardian_event "
        + "WHERE user_id=#{userId} AND event_key=#{eventKey}")
    Map<String, Object> selectEvent(@Param("userId") String userId, @Param("eventKey") String eventKey);

    @Insert("INSERT INTO guardian_event (user_id, event_key, event_type, response_json) "
        + "VALUES (#{userId}, #{eventKey}, #{eventType}, #{responseJson})")
    int insertEvent(@Param("userId") String userId, @Param("eventKey") String eventKey,
        @Param("eventType") String eventType, @Param("responseJson") String responseJson);
}
