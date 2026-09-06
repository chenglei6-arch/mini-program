package com.chenglei.miniprogram.integration;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 排行榜查询：非遗传承榜按 user_badge 徽章数排名（实时聚合，数据量小无需定时快照）。
 * 周榜口径为本周一（Asia/Shanghai）0 点起的解锁数；并列名次按最早解锁时间先后。
 */
@Mapper
public interface RankingsMapper {

    @Select("SELECT u.id AS userId, u.nickname AS nickname, COUNT(*) AS score "
        + "FROM user_badge ub JOIN app_user u ON u.id=ub.user_id "
        + "WHERE u.status=1 AND u.deleted=0 "
        + "GROUP BY u.id, u.nickname "
        + "ORDER BY score DESC, MIN(ub.unlocked_at) ASC "
        + "LIMIT 50")
    List<Map<String, Object>> selectTotalRanking();

    @Select("SELECT u.id AS userId, u.nickname AS nickname, COUNT(*) AS score "
        + "FROM user_badge ub JOIN app_user u ON u.id=ub.user_id "
        + "WHERE u.status=1 AND u.deleted=0 AND ub.unlocked_at >= #{since} "
        + "GROUP BY u.id, u.nickname "
        + "ORDER BY score DESC, MIN(ub.unlocked_at) ASC "
        + "LIMIT 50")
    List<Map<String, Object>> selectWeeklyRanking(@Param("since") LocalDate since);

    @Select("SELECT COUNT(*) FROM user_badge WHERE user_id=#{userId}")
    int countUserBadges(@Param("userId") String userId);

    /** 徽章数比当前用户多的用户数 + 1 即为当前排名；用户无徽章时返回 0。 */
    @Select("SELECT COUNT(*) + 1 FROM (SELECT user_id FROM user_badge GROUP BY user_id HAVING COUNT(*) > #{myCount}) t")
    int rankAbove(@Param("myCount") int myCount);

    @Select("SELECT COUNT(*) FROM user_badge WHERE user_id=#{userId} AND unlocked_at >= #{since}")
    int countUserWeeklyBadges(@Param("userId") String userId, @Param("since") LocalDate since);

    @Select("SELECT COUNT(*) + 1 FROM (SELECT user_id FROM user_badge WHERE unlocked_at >= #{since} "
        + "GROUP BY user_id HAVING COUNT(*) > #{myCount}) t")
    int weeklyRankAbove(@Param("myCount") int myCount, @Param("since") LocalDate since);
}
