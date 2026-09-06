package com.chenglei.miniprogram.badge;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserBadgeMapper {

    @Select("SELECT badge_id AS badgeId, unlocked_at AS unlockedAt FROM user_badge WHERE user_id=#{userId}")
    List<Map<String, Object>> selectByUser(@Param("userId") String userId);

    @Insert("INSERT IGNORE INTO user_badge (user_id, badge_id) VALUES (#{userId}, #{badgeId})")
    int insertUnlock(@Param("userId") String userId, @Param("badgeId") String badgeId);

    @Select("SELECT u.nickname AS nickname, b.name AS badgeName FROM user_badge ub "
        + "JOIN app_user u ON u.id=ub.user_id JOIN content_badge b ON b.id=ub.badge_id "
        + "ORDER BY ub.unlocked_at DESC LIMIT 5")
    List<Map<String, Object>> selectRecentUnlocks();
}
