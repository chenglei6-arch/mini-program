package com.chenglei.miniprogram.quiz;

import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ForestQuizMapper {

    @Select("SELECT user_id AS userId, completed_levels_json AS completedLevelsJson, current_level_id AS currentLevelId, question_index AS questionIndex, correct_count AS correctCount, version FROM forest_quiz_progress WHERE user_id=#{userId}")
    Map<String, Object> selectProgress(@Param("userId") String userId);

    @Insert("INSERT INTO forest_quiz_progress (user_id, completed_levels_json, current_level_id, question_index, correct_count, version) VALUES (#{userId}, #{completedLevelsJson}, #{currentLevelId}, #{questionIndex}, #{correctCount}, 1)")
    int insertProgress(@Param("userId") String userId, @Param("completedLevelsJson") String completedLevelsJson,
        @Param("currentLevelId") String currentLevelId, @Param("questionIndex") int questionIndex,
        @Param("correctCount") int correctCount);

    @Update("UPDATE forest_quiz_progress SET completed_levels_json=#{completedLevelsJson}, current_level_id=#{currentLevelId}, question_index=#{questionIndex}, correct_count=#{correctCount}, version=version+1, updated_at=CURRENT_TIMESTAMP(3) WHERE user_id=#{userId}")
    int updateProgress(@Param("userId") String userId, @Param("completedLevelsJson") String completedLevelsJson,
        @Param("currentLevelId") String currentLevelId, @Param("questionIndex") int questionIndex,
        @Param("correctCount") int correctCount);

    @Select("SELECT event_type AS eventType, response_json AS responseJson FROM forest_quiz_event WHERE user_id=#{userId} AND event_key=#{eventKey}")
    Map<String, Object> selectEvent(@Param("userId") String userId, @Param("eventKey") String eventKey);

    @Insert("INSERT INTO forest_quiz_event (user_id, event_key, event_type, response_json) VALUES (#{userId}, #{eventKey}, #{eventType}, #{responseJson})")
    int insertEvent(@Param("userId") String userId, @Param("eventKey") String eventKey,
        @Param("eventType") String eventType, @Param("responseJson") String responseJson);
}
