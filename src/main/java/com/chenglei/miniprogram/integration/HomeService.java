package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.badge.UserBadgeMapper;
import com.chenglei.miniprogram.common.db.RowValues;
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

    public HomeService(ContentCatalogService content, GuardianGameService guardianGameService,
        UserBadgeMapper userBadges) {
        this.content = content;
        this.guardianGameService = guardianGameService;
        this.userBadges = userBadges;
    }

    public Map<String, Object> home(String userId) {
        Map<String, Object> response = new LinkedHashMap<>(content.homeConfig());
        response.put("games", content.games());
        response.put("frogs", guardianGameService.applyUnlockState(content.frogs(), userId));
        response.put("patterns", content.patterns());
        response.put("activity", activityFeed());
        return response;
    }

    /** 蛙友动态：最近真实解锁流；还没有用户解锁时回退到种子引导文案。 */
    private List<String> activityFeed() {
        List<String> activity = new ArrayList<>();
        for (Map<String, Object> row : userBadges.selectRecentUnlocks()) {
            String nickname = RowValues.string(row, "nickname");
            String badgeName = RowValues.string(row, "badgeName");
            if (nickname.isBlank() || "null".equals(nickname)) nickname = "一位蛙友";
            activity.add(nickname + " 刚刚解锁了「" + badgeName + "」徽章");
        }
        return activity.isEmpty() ? content.activities() : activity;
    }
}
