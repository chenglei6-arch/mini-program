package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.auth.CurrentUser;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 非遗传承排行榜：按 user_badge 徽章数实时聚合（数据量小，无需定时快照任务）。
 * 总榜为上线以来累计；周榜口径为本周一（Asia/Shanghai）0 点起的解锁数。
 */
@Service
public class RankingsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int LIMIT = 50;

    private final RankingsMapper rankingsMapper;

    public RankingsService(RankingsMapper rankingsMapper) {
        this.rankingsMapper = rankingsMapper;
    }

    public Map<String, Object> rankings(String type, CurrentUser currentUser) {
        String normalizedType = type.equals("weekly") ? "weekly" : "total";
        LocalDate weekStart = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean weekly = normalizedType.equals("weekly");
        List<Map<String, Object>> rows = weekly
            ? rankingsMapper.selectWeeklyRanking(weekStart)
            : rankingsMapper.selectTotalRanking();

        List<Map<String, Object>> items = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            items.add(Map.of(
                "rank", rank++,
                "userId", String.valueOf(row.get("userId")),
                "nickname", String.valueOf(row.get("nickname")),
                "score", ((Number) row.get("score")).intValue()));
        }

        Integer myRank = myRank(currentUser, weekly, weekStart);

        // myRank 为 null 时由 jackson non_null 略去该字段，与契约一致。
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", normalizedType);
        response.put("updatedAt", Instant.now());
        response.put("items", items);
        response.put("myRank", myRank);
        response.put("page", 1);
        response.put("size", LIMIT);
        response.put("total", items.size());
        return response;
    }

    /** 无徽章的用户不上榜（myRank 为 null）；排名 = 徽章数更多的用户数 + 1。 */
    private Integer myRank(CurrentUser currentUser, boolean weekly, LocalDate weekStart) {
        if (currentUser == null) return null;
        int myCount = weekly
            ? rankingsMapper.countUserWeeklyBadges(currentUser.id(), weekStart)
            : rankingsMapper.countUserBadges(currentUser.id());
        if (myCount <= 0) return null;
        return weekly
            ? rankingsMapper.weeklyRankAbove(myCount, weekStart)
            : rankingsMapper.rankAbove(myCount);
    }
}
