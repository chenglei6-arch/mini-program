package com.chenglei.miniprogram.badge;

import com.chenglei.miniprogram.common.db.RowValues;
import com.chenglei.miniprogram.common.storage.GameProgressStore;
import com.chenglei.miniprogram.guardian.GuardianGameMapper;
import com.chenglei.miniprogram.integration.ContentCatalogService;
import com.chenglei.miniprogram.puzzle.FrogPuzzleStateJson;
import com.chenglei.miniprogram.quiz.ForestQuizMapper;
import com.chenglei.miniprogram.quiz.QuizStateJson;
import com.chenglei.miniprogram.story.StoryStateJson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 全站徽章解锁判定：从各游戏进度推导当前应解锁的徽章，落 user_badge 流水。
 * 触发时机：个人资料读取、守护神答题、剧情选择、拼图完成、闯关答题。
 *
 * 跨模块读取约定：剧情/拼图/闯关的进度 JSON 一律经对方的 *StateJson 组件解析
 * （字段名留在所属模块）；守护神/闯关的表状数据直接读 Mapper。不依赖对方
 * Service，避免与"事件后回调 evaluate"的调用方形成循环依赖。
 *
 * 口径说明：需求中"剪纸匠人/剪纸大师（5 款纹样各 N 次）"在游戏实现里对应
 * 完成不同林蛙的拼贴，因此以完成蛙数 1/5/9 作为三档剪纸徽章条件；
 * "说部守护者（付费解锁 3-5 章并通关）"暂以剧情全通关（finished）兜底，
 * 待付费层落地后再收紧条件。
 */
@Service
public class BadgeService {

    /** 剪纸徽章三档对应的完成蛙数。 */
    private static final Map<String, Integer> PAPER_FROG_REQUIREMENT = Map.of(
        "paper-beginner", 1, "paper-craftsman", 5, "paper-master", 9);
    private static final Map<String, Integer> GUARDIAN_REQUIREMENT = Map.of(
        "guardian-first", 1, "guardian-messenger", 3, "guardian-nine", 9);
    /** 闯关徽章与关卡 id 一一对应。 */
    private static final Map<String, String> QUIZ_LEVEL_REQUIREMENT = Map.of(
        "quiz-morphology", "morphology", "quiz-distribution", "distribution", "quiz-diet", "diet",
        "quiz-hibernation", "hibernation", "quiz-reproduction", "reproduction", "quiz-protection", "protection");

    private final ContentCatalogService content;
    private final GameProgressStore store;
    private final GuardianGameMapper guardianMapper;
    private final ForestQuizMapper quizMapper;
    private final UserBadgeMapper userBadges;
    private final StoryStateJson storyStateJson;
    private final FrogPuzzleStateJson puzzleStateJson;
    private final QuizStateJson quizStateJson;

    public BadgeService(ContentCatalogService content, GameProgressStore store,
        GuardianGameMapper guardianMapper, ForestQuizMapper quizMapper, UserBadgeMapper userBadges,
        StoryStateJson storyStateJson, FrogPuzzleStateJson puzzleStateJson, QuizStateJson quizStateJson) {
        this.content = content;
        this.store = store;
        this.guardianMapper = guardianMapper;
        this.quizMapper = quizMapper;
        this.userBadges = userBadges;
        this.storyStateJson = storyStateJson;
        this.puzzleStateJson = puzzleStateJson;
        this.quizStateJson = quizStateJson;
    }

    public record Evaluation(List<Map<String, Object>> badges, List<Map<String, Object>> newlyUnlocked) { }

    /**
     * 同步用户徽章并返回全量目录（含 unlocked 与 unlockedAt）。
     * newlyUnlocked 列表是本次调用新写入 user_badge 的徽章。
     */
    public Evaluation evaluate(String userId) {
        Map<String, Boolean> conditions = conditions(userId);
        List<Map<String, Object>> catalog = content.badges();
        Map<String, Instant> unlocked = unlockedAt(userId);

        List<Map<String, Object>> badges = new ArrayList<>();
        List<Map<String, Object>> newly = new ArrayList<>();
        for (Map<String, Object> item : catalog) {
            String badgeId = String.valueOf(item.get("id"));
            Map<String, Object> entry = new LinkedHashMap<>(item);
            Instant at = unlocked.get(badgeId);
            boolean satisfies = Boolean.TRUE.equals(conditions.getOrDefault(badgeId, false));
            if (at == null && satisfies) {
                userBadges.insertUnlock(userId, badgeId);
                at = Instant.now();
                newly.add(Map.of("id", badgeId, "name", String.valueOf(item.get("name")),
                    "level", String.valueOf(item.get("level"))));
            }
            entry.put("unlocked", at != null);
            entry.put("unlockedAt", at);
            badges.add(entry);
        }
        return new Evaluation(List.copyOf(badges), List.copyOf(newly));
    }

    private Map<String, Boolean> conditions(String userId) {
        Map<String, Boolean> conditions = new HashMap<>();

        int completedFrogs = puzzleStateJson.completedFrogCount(store.loadState(userId, "frog-puzzle"));
        boolean paperCuttingDone = paperCuttingCompleted(userId);
        PAPER_FROG_REQUIREMENT.forEach((badgeId, required) -> conditions.put(badgeId,
            completedFrogs >= required || (required == 1 && paperCuttingDone)));

        String storyState = store.loadState(userId, "story");
        conditions.put("story-listener", storyStateJson.completedRouteCount(storyState) >= 1);
        conditions.put("story-inheritor", storyStateJson.completedRouteCount(storyState) >= 2);
        conditions.put("story-guardian", storyStateJson.finished(storyState));

        int collectionCount = guardianMapper.selectCollectedFrogIds(userId).size();
        GUARDIAN_REQUIREMENT.forEach((badgeId, required) -> conditions.put(badgeId, collectionCount >= required));

        List<String> quizLevels = quizCompletedLevels(userId);
        QUIZ_LEVEL_REQUIREMENT.forEach((badgeId, levelId) -> conditions.put(badgeId, quizLevels.contains(levelId)));

        return conditions;
    }

    private Map<String, Instant> unlockedAt(String userId) {
        Map<String, Instant> result = new HashMap<>();
        for (Map<String, Object> row : userBadges.selectByUser(userId)) {
            Object at = RowValues.valueOf(row, "unlockedAt");
            if (at instanceof Date date) {
                result.put(String.valueOf(row.get("badgeId")), date.toInstant());
            } else if (at instanceof Instant instant) {
                result.put(String.valueOf(row.get("badgeId")), instant);
            }
        }
        return result;
    }

    private boolean paperCuttingCompleted(String userId) {
        // 完成标记在 game_progress.completed 列上，无需解析状态 JSON。
        return store.isCompleted(userId, "paper-cutting");
    }

    private List<String> quizCompletedLevels(String userId) {
        Map<String, Object> row = quizMapper.selectProgress(userId);
        if (row == null) return List.of();
        return quizStateJson.completedLevelIds(String.valueOf(row.get("completedLevelsJson")));
    }
}
