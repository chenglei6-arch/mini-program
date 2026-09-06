package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.auth.SessionService;
import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.badge.UserBadgeMapper;
import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.common.storage.GameProgressStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chenglei.miniprogram.guardian.GuardianGameService;
import com.chenglei.miniprogram.quiz.ForestQuizGameService;
import com.chenglei.miniprogram.story.StoryGameService;
import com.chenglei.miniprogram.unlock.UnlockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class IntegrationController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SessionService sessions;
    private final StoryGameService storyGameService;
    private final GuardianGameService guardianGameService;
    private final ForestQuizGameService forestQuizGameService;
    private final ContentCatalogService content;
    private final GameProgressStore store;
    private final ObjectMapper objectMapper;
    private final BadgeService badgeService;
    private final RankingsMapper rankingsMapper;
    private final UserBadgeMapper userBadges;
    private final UnlockService unlockService;

    public IntegrationController(SessionService sessions, StoryGameService storyGameService,
        GuardianGameService guardianGameService, ForestQuizGameService forestQuizGameService, ContentCatalogService content,
        GameProgressStore store, ObjectMapper objectMapper, BadgeService badgeService,
        RankingsMapper rankingsMapper, UserBadgeMapper userBadges, UnlockService unlockService) {
        this.sessions = sessions;
        this.storyGameService = storyGameService;
        this.guardianGameService = guardianGameService;
        this.forestQuizGameService = forestQuizGameService;
        this.content = content;
        this.store = store;
        this.objectMapper = objectMapper;
        this.badgeService = badgeService;
        this.rankingsMapper = rankingsMapper;
        this.userBadges = userBadges;
        this.unlockService = unlockService;
    }

    @GetMapping("/home/summary")
    public ApiResponse<Map<String, Object>> home(Authentication authentication) {
        String userId = user(authentication).id();
        Map<String, Object> response = new LinkedHashMap<>(content.homeConfig());
        response.put("games", content.games());
        response.put("frogs", guardianGameService.applyUnlockState(content.frogs(), userId));
        response.put("patterns", content.patterns());
        response.put("activity", activityFeed());
        return ApiResponse.success(response);
    }

    /** 蛙友动态：最近真实解锁流；还没有用户解锁时回退到种子引导文案。 */
    private List<String> activityFeed() {
        List<String> activity = new ArrayList<>();
        for (Map<String, Object> row : userBadges.selectRecentUnlocks()) {
            String nickname = String.valueOf(valueOf(row, "nickname"));
            String badgeName = String.valueOf(valueOf(row, "badgeName"));
            if (nickname.isBlank() || "null".equals(nickname)) nickname = "一位蛙友";
            activity.add(nickname + " 刚刚解锁了「" + badgeName + "」徽章");
        }
        return activity.isEmpty() ? content.activities() : activity;
    }

    @GetMapping("/content/frogs")
    public ApiResponse<Map<String, Object>> frogs(Authentication authentication) {
        String userId = user(authentication).id();
        List<Map<String, Object>> frogs = guardianGameService.applyUnlockState(content.frogs(), userId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", frogs);
        response.put("total", frogs.size());
        return ApiResponse.success(response);
    }

    @GetMapping("/content/frogs/{frogId}")
    public ApiResponse<Map<String, Object>> frogDetail(Authentication authentication, @PathVariable String frogId) {
        Map<String, Object> frog = content.frog(frogId);
        if (frog == null) throw new BusinessException(ErrorCode.NOT_FOUND, "林蛙不存在");
        return ApiResponse.success(guardianGameService.applyUnlockState(frog, user(authentication).id()));
    }

    @GetMapping("/me/profile")
    public ApiResponse<Map<String, Object>> profile(Authentication authentication) {
        CurrentUser user = sessions.profileOf(user(authentication).id());
        BadgeService.Evaluation evaluation = badgeService.evaluate(user.id());
        List<Map<String, Object>> badges = evaluation.badges();
        int frogCount = guardianGameService.progress(user.id()).get("completed") instanceof Number count ? count.intValue() : 0;
        int badgeCount = (int) badges.stream().filter(badge -> Boolean.TRUE.equals(badge.get("unlocked"))).count();
        return ApiResponse.success(Map.of("user", user, "stats", Map.of("frogs", frogCount, "frogsTotal", 9, "patterns", 0,
            "patternsTotal", content.patterns().size(), "badges", badgeCount, "badgesTotal", badges.size(), "contribution", "0.00"),
            "badges", badges, "orders", List.of()));
    }

    @PatchMapping("/me/profile")
    public ApiResponse<CurrentUser> updateProfile(Authentication authentication,
        @Valid @RequestBody ProfilePatch patch) {
        String userId = user(authentication).id();
        sessions.updateProfile(userId, patch.nickname(), patch.avatarUrl());
        return ApiResponse.success(sessions.profileOf(userId));
    }

    @GetMapping("/games/{gameId}/progress")
    public ApiResponse<Map<String, Object>> gameProgress(Authentication authentication, @PathVariable String gameId) {
        ensureGame(gameId);
        if (gameId.equals("story")) return ApiResponse.success(storyGameService.progress(authentication));
        if (gameId.equals("guardian")) return ApiResponse.success(guardianGameService.progress(user(authentication).id()));
        if (gameId.equals("forest-quiz")) return ApiResponse.success(forestQuizGameService.progress(user(authentication).id()));
        int completed = completedFromState(store.loadState(user(authentication).id(), gameId));
        return ApiResponse.success(Map.of("gameId", gameId, "completed", completed, "total", gameId.equals("story") ? 5 : 1,
            "finished", completed > 0, "version", 1, "updatedAt", Instant.now()));
    }

    @PostMapping("/games/{gameId}/events")
    public ApiResponse<Map<String, Object>> gameEvent(Authentication authentication, @PathVariable String gameId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody GameEvent event) {
        ensureGame(gameId);
        if (gameId.equals("story")) {
            if (event.type().equals("reset")) return ApiResponse.success(storyGameService.reset(authentication));
            if (!event.type().equals("story_choice") || event.payload() == null || !(event.payload().get("choiceId") instanceof String choiceId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "故事事件必须包含 choiceId");
            }
            return ApiResponse.success(storyGameService.choose(authentication, idempotencyKey, choiceId));
        }
        if (gameId.equals("guardian")) {
            String userId = user(authentication).id();
            if (event.type().equals("draw")) return ApiResponse.success(guardianGameService.draw(userId, idempotencyKey));
            if (event.type().equals("share")) return ApiResponse.success(guardianGameService.claimShareBonus(userId, idempotencyKey));
            if (event.type().equals("answer") && event.payload() != null
                && event.payload().get("roundId") instanceof String roundId
                && event.payload().get("pattern") instanceof String pattern) {
                return ApiResponse.success(guardianGameService.answer(userId, idempotencyKey, roundId, pattern));
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "守护神事件仅支持 draw、answer 或 share；answer 必须包含 roundId 和 pattern");
        }
        if (gameId.equals("forest-quiz")) {
            if (event.type().equals("quiz_answer") && event.payload() != null
                && event.payload().get("levelId") instanceof String levelId
                && event.payload().get("questionId") instanceof String questionId
                && event.payload().get("optionId") instanceof String optionId) {
                return ApiResponse.success(forestQuizGameService.answer(user(authentication).id(), idempotencyKey,
                    levelId, questionId, optionId));
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识闯关事件必须包含 levelId、questionId 和 optionId");
        }
        String userId = user(authentication).id();
        String previous = store.loadStateForUpdate(userId, gameId);
        int completed = completedFromState(previous);
        if ("completed".equals(event.type()) || "unlocked".equals(event.type())) {
            completed = 1;
            store.saveState(userId, gameId, stateJson(Map.of("completed", completed)), true);
        }
        return ApiResponse.success(Map.of("accepted", true, "duplicated", false, "gameId", gameId,
            "progress", Map.of("gameId", gameId, "completed", completed, "total", gameId.equals("story") ? 5 : 1,
                "finished", completed > 0, "version", 1, "updatedAt", Instant.now())));
    }

    @PostMapping("/unlocks/redeem")
    public ApiResponse<Map<String, Object>> redeem(Authentication authentication, @Valid @RequestBody UnlockRequest request) {
        String userId = user(authentication).id();
        return ApiResponse.success(unlockService.redeem(userId, request.code()));
    }

    @GetMapping("/rankings")
    public ApiResponse<Map<String, Object>> rankings(Authentication authentication,
        @RequestParam(defaultValue = "total") String type) {
        String normalizedType = type.equals("weekly") ? "weekly" : "total";
        LocalDate weekStart = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean weekly = normalizedType.equals("weekly");
        List<Map<String, Object>> rows = weekly
            ? rankingsMapper.selectWeeklyRanking(weekStart)
            : rankingsMapper.selectTotalRanking();

        List<Map<String, Object>> items = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Object scoreValue = valueOf(row, "score");
            int score = scoreValue instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(scoreValue));
            items.add(Map.of(
                "rank", rank++,
                "userId", String.valueOf(valueOf(row, "userId")),
                "nickname", String.valueOf(valueOf(row, "nickname")),
                "score", score));
        }

        Integer myRank = null;
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser current) {
            int myCount = weekly
                ? rankingsMapper.countUserWeeklyBadges(current.id(), weekStart)
                : rankingsMapper.countUserBadges(current.id());
            if (myCount > 0) {
                myRank = weekly
                    ? rankingsMapper.weeklyRankAbove(myCount, weekStart)
                    : rankingsMapper.rankAbove(myCount);
            }
        }

        // myRank 为 null 时由 jackson non_null 略去该字段，与契约一致。
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", normalizedType);
        response.put("updatedAt", Instant.now());
        response.put("items", items);
        response.put("myRank", myRank);
        response.put("page", 1);
        response.put("size", 50);
        response.put("total", items.size());
        return ApiResponse.success(response);
    }

    @GetMapping("/welfare/summary")
    public ApiResponse<Map<String, Object>> welfare() {
        Map<String, Object> response = new LinkedHashMap<>(content.welfare());
        List<Map<String, Object>> reports = new ArrayList<>();
        for (Map<String, Object> row : content.welfareReports()) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Object publishedAt = valueOf(row, "publishedAt");
            item.put("date", publishedAt instanceof Date date
                ? date.toInstant().atZone(ZONE).toLocalDate().toString()
                : String.valueOf(publishedAt));
            reports.add(item);
        }
        response.put("reports", reports);
        return ApiResponse.success(response);
    }

    /** H2（DATABASE_TO_LOWER）会把列别名转小写，MySQL 保留大小写，因此按键大小写不敏感取值。 */
    private static Object valueOf(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value != null || values.containsKey(key)) return value;
        return values.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(key))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private CurrentUser user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    private int completedFromState(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) return 0;
        try {
            return objectMapper.readTree(stateJson).path("completed").asInt(0);
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    private String stateJson(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("游戏状态序列化失败", e);
        }
    }

    private static void ensureGame(String gameId) {
        if (!List.of("paper-cutting", "story", "guardian", "forest-quiz").contains(gameId)) throw new IllegalArgumentException("游戏不存在");
    }

    public record ProfilePatch(@Size(min = 1, max = 64) String nickname, @Size(max = 512) String avatarUrl) { }
    public record GameEvent(@NotBlank String type, Map<String, Object> payload) { }
    public record UnlockRequest(@NotBlank @Size(min = 6, max = 64) String code) { }
}
