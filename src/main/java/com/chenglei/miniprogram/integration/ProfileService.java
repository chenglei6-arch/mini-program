package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.auth.SessionService;
import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.guardian.GuardianGameService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 用户中心资料聚合：头像昵称、收集进度、徽章墙。
 * 订单依赖支付能力，后端暂返回空数组。
 */
@Service
public class ProfileService {

    private final SessionService sessions;
    private final GuardianGameService guardianGameService;
    private final BadgeService badgeService;
    private final ContentCatalogService content;

    public ProfileService(SessionService sessions, GuardianGameService guardianGameService,
        BadgeService badgeService, ContentCatalogService content) {
        this.sessions = sessions;
        this.guardianGameService = guardianGameService;
        this.badgeService = badgeService;
        this.content = content;
    }

    public Map<String, Object> profile(String userId) {
        CurrentUser user = sessions.profileOf(userId);
        BadgeService.Evaluation evaluation = badgeService.evaluate(user.id());
        List<Map<String, Object>> badges = evaluation.badges();
        int frogCount = guardianGameService.progress(user.id()).get("completed") instanceof Number count
            ? count.intValue() : 0;
        int badgeCount = (int) badges.stream().filter(badge -> Boolean.TRUE.equals(badge.get("unlocked"))).count();
        return Map.of("user", user, "stats", Map.of("frogs", frogCount, "frogsTotal", 9, "patterns", 0,
            "patternsTotal", content.patterns().size(), "badges", badgeCount, "badgesTotal", badges.size(),
            "contribution", "0.00"), "badges", badges, "orders", List.of());
    }

    public CurrentUser updateProfile(String userId, String nickname, String avatarUrl) {
        sessions.updateProfile(userId, nickname, avatarUrl);
        return sessions.profileOf(userId);
    }
}
