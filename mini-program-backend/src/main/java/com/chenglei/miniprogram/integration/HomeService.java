package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.badge.BadgeCatalog;
import com.chenglei.miniprogram.badge.UserBadgeMapper;
import com.chenglei.miniprogram.guardian.GuardianGameService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 首页聚合：品牌配置、游戏入口、角色墙、蛙友动态。
 * 聚合本身是业务逻辑，因此放在服务层而非控制器。
 */
@Service
public class HomeService {

    private final ContentCatalogService content;
    private final GuardianGameService guardianGameService;
    private final UserBadgeMapper userBadges;
    private final BadgeCatalog badgeCatalog;

    public HomeService(ContentCatalogService content, GuardianGameService guardianGameService,
        UserBadgeMapper userBadges, BadgeCatalog badgeCatalog) {
        this.content = content;
        this.guardianGameService = guardianGameService;
        this.userBadges = userBadges;
        this.badgeCatalog = badgeCatalog;
    }

    public Map<String, Object> home(String userId) {
        Map<String, Object> response = new LinkedHashMap<>(content.homeConfig());
        response.put("fundAmount", content.welfare().get("fundAmount"));
        response.put("games", content.games());
        response.put("frogs", guardianGameService.applyUnlockState(content.frogs(), userId));
        response.put("activity", activityFeed());
        return response;
    }

    /** 蛙友动态：只展示真实的徽章解锁流水；没有解锁就是空列表，不编造演示文案。 */
    private List<String> activityFeed() {
        List<String> activity = new ArrayList<>();
        for (UserBadgeMapper.RecentUnlockRow row : userBadges.selectRecentUnlocks()) {
            activity.add(row.getNickname() + " 刚刚解锁了「" + badgeCatalog.nameOf(row.getBadgeId()) + "」徽章");
        }
        return activity;
    }
}
