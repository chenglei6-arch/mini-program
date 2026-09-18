package com.chenglei.miniprogram.badge;

import com.chenglei.miniprogram.common.storage.GameProgressStore;
import com.chenglei.miniprogram.guardian.GuardianGameMapper;
import com.chenglei.miniprogram.puzzle.FrogPuzzleStateJson;
import com.chenglei.miniprogram.quiz.ForestQuizMapper;
import com.chenglei.miniprogram.quiz.QuizStateJson;
import com.chenglei.miniprogram.story.StoryStateJson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 全站徽章解锁判定：按 {@link BadgeCatalog} 的条件逐一评估各游戏进度，落 user_badge 流水。
 * 触发时机：个人资料读取、守护神答题、剧情选择、拼图完成、闯关答题。
 *
 * 跨模块读取约定：剧情/拼图/闯关的进度 JSON 一律经对方的 *StateJson 组件解析
 * （字段名留在所属模块）；守护神/闯关的表状数据直接读 Mapper。不依赖对方
 * Service，避免与"事件后回调 evaluate"的调用方形成循环依赖。
 */
@Service
public class BadgeService {

    private final GameProgressStore store;
    private final GuardianGameMapper guardianMapper;
    private final ForestQuizMapper quizMapper;
    private final UserBadgeMapper userBadges;
    private final StoryStateJson storyStateJson;
    private final FrogPuzzleStateJson puzzleStateJson;
    private final QuizStateJson quizStateJson;
    private final BadgeCatalog catalog;

    public BadgeService(GameProgressStore store, GuardianGameMapper guardianMapper, ForestQuizMapper quizMapper,
        UserBadgeMapper userBadges, StoryStateJson storyStateJson, FrogPuzzleStateJson puzzleStateJson,
        QuizStateJson quizStateJson, BadgeCatalog catalog) {
        this.store = store;
        this.guardianMapper = guardianMapper;
        this.quizMapper = quizMapper;
        this.userBadges = userBadges;
        this.storyStateJson = storyStateJson;
        this.puzzleStateJson = puzzleStateJson;
        this.quizStateJson = quizStateJson;
        this.catalog = catalog;
    }

    public record Evaluation(List<Map<String, Object>> badges, List<Map<String, Object>> newlyUnlocked) { }

    /**
     * 同步用户徽章并返回全量目录（含 unlocked 与 unlockedAt）。
     * newlyUnlocked 列表是本次调用新写入 user_badge 的徽章。
     */
    public Evaluation evaluate(String userId) {
        Map<String, Boolean> conditions = conditions(userId);
        Map<String, Instant> unlocked = unlockedAt(userId);

        List<Map<String, Object>> badges = new ArrayList<>();
        List<Map<String, Object>> newly = new ArrayList<>();
        for (BadgeCatalog.Badge badge : catalog.all()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", badge.id());
            entry.put("name", badge.name());
            entry.put("level", badge.level());
            Instant at = unlocked.get(badge.id());
            if (at == null && Boolean.TRUE.equals(conditions.get(badge.id()))) {
                userBadges.insertUnlock(userId, badge.id());
                at = Instant.now();
                newly.add(Map.of("id", badge.id(), "name", badge.name(), "level", badge.level()));
            }
            entry.put("unlocked", at != null);
            entry.put("unlockedAt", at);
            badges.add(entry);
        }
        return new Evaluation(List.copyOf(badges), List.copyOf(newly));
    }

    private Map<String, Boolean> conditions(String userId) {
        int completedFrogs = puzzleStateJson.completedFrogCount(store.loadState(userId, "frog-puzzle"));
        String storyState = store.loadState(userId, "story");
        int completedRoutes = storyStateJson.completedRouteCount(storyState);
        boolean storyFinished = storyStateJson.finished(storyState);
        int collectedGuardians = guardianMapper.selectCollectedFrogIds(userId).size();
        List<String> quizLevels = quizCompletedLevels(userId);

        Map<String, Boolean> conditions = new HashMap<>();
        for (BadgeCatalog.Badge badge : catalog.all()) {
            BadgeCatalog.Condition condition = badge.condition();
            conditions.put(badge.id(), switch (condition.type()) {
                case "frogs" -> completedFrogs >= condition.count();
                case "story-routes" -> completedRoutes >= condition.count();
                case "story-finished" -> storyFinished;
                case "guardian" -> collectedGuardians >= condition.count();
                case "quiz-level" -> quizLevels.contains(condition.levelId());
                default -> throw new IllegalStateException("未知徽章条件类型：" + condition.type());
            });
        }
        return conditions;
    }

    private Map<String, Instant> unlockedAt(String userId) {
        Map<String, Instant> result = new HashMap<>();
        for (UserBadgeMapper.UserBadgeRow row : userBadges.selectByUser(userId)) {
            result.put(row.getBadgeId(), row.getUnlockedAt());
        }
        return result;
    }

    private List<String> quizCompletedLevels(String userId) {
        Map<String, Object> row = quizMapper.selectProgress(userId);
        if (row == null) return List.of();
        return quizStateJson.completedLevelIds((String) row.get("completedLevelsJson"));
    }
}
