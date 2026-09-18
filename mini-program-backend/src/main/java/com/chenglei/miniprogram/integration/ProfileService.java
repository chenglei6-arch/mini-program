package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.auth.SessionService;
import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.guardian.GuardianGameService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 用户中心资料聚合：头像昵称、收集进度、徽章墙。订单在商城接口单独获取。 */
@Service
public class ProfileService {

    private final SessionService sessions;
    private final GuardianGameService guardianGameService;
    private final BadgeService badgeService;

    public ProfileService(SessionService sessions, GuardianGameService guardianGameService,
        BadgeService badgeService) {
        this.sessions = sessions;
        this.guardianGameService = guardianGameService;
        this.badgeService = badgeService;
    }

    public Map<String, Object> profile(String userId) {
        CurrentUser user = sessions.profileOf(userId);
        BadgeService.Evaluation evaluation = badgeService.evaluate(user.id());
        List<Map<String, Object>> badges = evaluation.badges();
        Map<String, Object> guardianProgress = guardianGameService.progress(user.id());
        int frogCount = (int) guardianProgress.get("completed");
        int frogTotal = (int) guardianProgress.get("total");
        int badgeCount = (int) badges.stream().filter(badge -> Boolean.TRUE.equals(badge.get("unlocked"))).count();
        return Map.of(
            "user", user,
            "stats", Map.of("frogs", frogCount, "frogsTotal", frogTotal,
                "badges", badgeCount, "badgesTotal", badges.size()),
            "badges", badges);
    }

    public CurrentUser updateProfile(String userId, String nickname, String avatarUrl) {
        sessions.updateProfile(userId, nickname, avatarUrl);
        return sessions.profileOf(userId);
    }
}
