package com.chenglei.miniprogram.badge;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserBadgeMapper {

    @Select("SELECT badge_id AS \"badgeId\", unlocked_at AS \"unlockedAt\" FROM user_badge WHERE user_id=#{userId}")
    List<UserBadgeRow> selectByUser(@Param("userId") String userId);

    @Insert("INSERT IGNORE INTO user_badge (user_id, badge_id) VALUES (#{userId}, #{badgeId})")
    int insertUnlock(@Param("userId") String userId, @Param("badgeId") String badgeId);

    /** 首页蛙友动态用的最近解锁流水；昵称为空的账号不进动态。 */
    @Select("SELECT u.nickname AS \"nickname\", ub.badge_id AS \"badgeId\" FROM user_badge ub "
        + "JOIN app_user u ON u.id=ub.user_id "
        + "WHERE u.nickname IS NOT NULL AND u.nickname <> '' "
        + "ORDER BY ub.unlocked_at DESC LIMIT 5")
    List<RecentUnlockRow> selectRecentUnlocks();

    class UserBadgeRow {
        private String badgeId;
        private Instant unlockedAt;

        public String getBadgeId() {
            return badgeId;
        }

        public Instant getUnlockedAt() {
            return unlockedAt;
        }
    }

    class RecentUnlockRow {
        private String nickname;
        private String badgeId;

        public String getNickname() {
            return nickname;
        }

        public String getBadgeId() {
            return badgeId;
        }
    }
}
