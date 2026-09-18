package com.chenglei.miniprogram.guardian;

import com.chenglei.miniprogram.badge.BadgeCatalog;
import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.integration.ContentCatalogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuardianGameService {

    private static final String GAME_ID = "guardian";
    private static final ZoneId GAME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DAILY_FREE_DRAWS = 3;
    private static final int SHARE_BONUS_DRAWS = 1;

    private final GuardianGameMapper mapper;
    private final ContentCatalogService content;
    private final ObjectMapper objectMapper;
    private final BadgeService badgeService;
    private final BadgeCatalog badgeCatalog;

    public GuardianGameService(GuardianGameMapper mapper, ContentCatalogService content, ObjectMapper objectMapper,
        BadgeService badgeService, BadgeCatalog badgeCatalog) {
        this.mapper = mapper;
        this.content = content;
        this.objectMapper = objectMapper;
        this.badgeService = badgeService;
        this.badgeCatalog = badgeCatalog;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> progress(String userId) {
        return buildProgress(userId, LocalDate.now(GAME_ZONE), false);
    }

    @Transactional
    public Map<String, Object> draw(String userId, String idempotencyKey) {
        return mutate(userId, idempotencyKey, "draw", () -> {
            LocalDate today = LocalDate.now(GAME_ZONE);
            GuardianGameMapper.GuardianRoundRow activeRound = mapper.selectRoundForUpdate(userId);
            if (activeRound != null) {
                return eventResult(buildProgress(userId, today, true), Map.of(
                    "drawn", false,
                    "message", "请先完成当前守护神纹样题"
                ));
            }

            Set<String> collectedIds = new LinkedHashSet<>(mapper.selectCollectedFrogIds(userId));
            List<Map<String, Object>> frogs = formalFrogs();
            List<Map<String, Object>> candidates = frogs.stream()
                .filter(frog -> !collectedIds.contains(frog.get("id")))
                .toList();
            if (candidates.isEmpty()) {
                throw new BusinessException(ErrorCode.CONFLICT, "九只林蛙守护神已全部收集");
            }

            Map<String, Object> dailyState = dailyStateForUpdate(userId, today);
            int drawsUsed = drawsUsed(dailyState);
            boolean shareBonusClaimed = shareBonusClaimed(dailyState);
            if (remainingDraws(drawsUsed, shareBonusClaimed) <= 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "今日摇一摇次数已用完，分享后可额外获得 1 次");
            }

            Map<String, Object> frog = candidates.get((int) (Math.random() * candidates.size()));
            List<String> options = buildOptions(frog, frogs);
            String roundId = UUID.randomUUID().toString();
            mapper.updateDrawsUsed(userId, today, drawsUsed + 1);
            mapper.insertRound(userId, roundId, String.valueOf(frog.get("id")), writeOptions(options));

            return eventResult(buildProgress(userId, today, true), Map.of(
                "drawn", true,
                "message", "守护神已现身，请选择对应纹样"
            ));
        });
    }

    @Transactional
    public Map<String, Object> answer(String userId, String idempotencyKey, String roundId, String pattern) {
        return mutate(userId, idempotencyKey, "answer", () -> {
            LocalDate today = LocalDate.now(GAME_ZONE);
            GuardianGameMapper.GuardianRoundRow round = mapper.selectRoundForUpdate(userId);
            if (round == null) throw new BusinessException(ErrorCode.CONFLICT, "当前没有待回答的守护神题目");
            if (!round.getRoundId().equals(roundId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "守护神题目已更新，请按当前题目作答");
            }

            Map<String, Object> frog = frogById(round.getFrogId());
            if (frog == null) throw new BusinessException(ErrorCode.NOT_FOUND, "守护神角色不存在");
            if (!String.valueOf(frog.get("pattern")).equals(pattern)) {
                return eventResult(buildProgress(userId, today, true), Map.of(
                    "correct", false,
                    "message", "纹样不对，再试一次"
                ));
            }

            mapper.insertCollection(userId, String.valueOf(frog.get("id")));
            mapper.deleteRound(userId);
            return eventResult(buildProgress(userId, today, true), Map.of(
                "correct", true,
                "newlyUnlocked", true,
                "guardianCard", cardFor(frog),
                "newlyUnlockedBadges", badgeService.evaluate(userId).newlyUnlocked(),
                "message", "守护成功"
            ));
        });
    }

    @Transactional
    public Map<String, Object> claimShareBonus(String userId, String idempotencyKey) {
        return mutate(userId, idempotencyKey, "share", () -> {
            LocalDate today = LocalDate.now(GAME_ZONE);
            Map<String, Object> dailyState = dailyStateForUpdate(userId, today);
            if (shareBonusClaimed(dailyState)) {
                throw new BusinessException(ErrorCode.CONFLICT, "今日分享额外次数已领取");
            }
            mapper.claimShareBonus(userId, today);
            return eventResult(buildProgress(userId, today, true), Map.of(
                "shareBonusGranted", SHARE_BONUS_DRAWS,
                "message", "分享成功，已获得 1 次额外摇一摇机会"
            ));
        });
    }

    public List<Map<String, Object>> applyUnlockState(List<Map<String, Object>> frogs, String userId) {
        Set<String> collected = new LinkedHashSet<>(mapper.selectCollectedFrogIds(userId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> frog : frogs) {
            Map<String, Object> item = new LinkedHashMap<>(frog);
            item.put("unlocked", collected.contains(item.get("id")));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> applyUnlockState(Map<String, Object> frog, String userId) {
        Map<String, Object> result = new LinkedHashMap<>(frog);
        result.put("unlocked", mapper.selectCollectedFrogIds(userId).contains(result.get("id")));
        return result;
    }

    private Map<String, Object> buildProgress(String userId, LocalDate date, boolean lockRound) {
        Map<String, Object> dailyState = lockRound ? dailyStateForUpdate(userId, date) : mapper.selectDailyState(userId, date);
        int drawsUsed = dailyState == null ? 0 : drawsUsed(dailyState);
        boolean shareBonusClaimed = dailyState != null && shareBonusClaimed(dailyState);
        List<String> collectedIds = mapper.selectCollectedFrogIds(userId);
        GuardianGameMapper.GuardianRoundRow round = lockRound ? mapper.selectRoundForUpdate(userId) : mapper.selectRound(userId);
        List<Map<String, Object>> frogs = formalFrogs();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("gameDate", date);
        state.put("dailyFreeDraws", DAILY_FREE_DRAWS);
        state.put("drawsUsed", drawsUsed);
        state.put("remainingDraws", remainingDraws(drawsUsed, shareBonusClaimed));
        state.put("shareBonusClaimed", shareBonusClaimed);
        state.put("canClaimShareBonus", !shareBonusClaimed);
        state.put("collectedFrogIds", collectedIds);
        state.put("collection", collectedFrogs(collectedIds));
        state.put("activeChallenge", round == null ? null : challengeFor(round));
        state.put("badges", guardianBadgeStates(collectedIds.size()));

        return Map.of(
            "gameId", GAME_ID,
            "completed", collectedIds.size(),
            "total", frogs.size(),
            "finished", collectedIds.size() == frogs.size(),
            "version", 1,
            "updatedAt", Instant.now(),
            "state", state
        );
    }

    private Map<String, Object> eventResult(Map<String, Object> progress, Map<String, Object> action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("duplicated", false);
        result.put("gameId", GAME_ID);
        result.put("progress", progress);
        result.put("action", action);
        return result;
    }

    private Map<String, Object> mutate(String userId, String idempotencyKey, String eventType,
        Supplier<Map<String, Object>> operation) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return operation.get();
        Map<String, Object> existing = mapper.selectEvent(userId, idempotencyKey);
        if (existing != null) {
            return storedResult(existing, eventType);
        }
        Map<String, Object> response;
        try {
            response = operation.get();
        } catch (BusinessException error) {
            // 操作因并发先至而失败（如分享奖励已被同键请求领取）时，返回已存结果而不是冲突。
            Map<String, Object> recorded = mapper.selectEvent(userId, idempotencyKey);
            if (recorded != null) return storedResult(recorded, eventType);
            throw error;
        }
        try {
            mapper.insertEvent(userId, idempotencyKey, eventType, writeJson(response));
        } catch (DuplicateKeyException error) {
            // 并发同键：另一事务已记录响应，返回已存结果而不是报 500。
            Map<String, Object> recorded = mapper.selectEvent(userId, idempotencyKey);
            if (recorded != null) return storedResult(recorded, eventType);
        }
        return response;
    }

    private Map<String, Object> storedResult(Map<String, Object> row, String eventType) {
        if (!eventType.equals(String.valueOf(row.get("eventType")))) {
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键不能用于不同的守护神操作");
        }
        Map<String, Object> response = readMap(String.valueOf(row.get("responseJson")));
        response.put("duplicated", true);
        return response;
    }

    private Map<String, Object> dailyStateForUpdate(String userId, LocalDate date) {
        Map<String, Object> state = mapper.selectDailyStateForUpdate(userId, date);
        if (state != null) return state;
        mapper.insertDailyState(userId, date);
        return mapper.selectDailyStateForUpdate(userId, date);
    }

    private static int drawsUsed(Map<String, Object> dailyState) {
        return ((Number) dailyState.get("drawsUsed")).intValue();
    }

    private static boolean shareBonusClaimed(Map<String, Object> dailyState) {
        return ((Number) dailyState.get("shareBonusClaimed")).intValue() == 1;
    }

    private List<Map<String, Object>> formalFrogs() {
        return content.guardianFrogs();
    }

    private Map<String, Object> frogById(String frogId) {
        return formalFrogs().stream().filter(frog -> frogId.equals(frog.get("id"))).findFirst().orElse(null);
    }

    private List<String> buildOptions(Map<String, Object> frog, List<Map<String, Object>> frogs) {
        List<String> wrongPatterns = frogs.stream()
            .map(item -> String.valueOf(item.get("pattern")))
            .filter(pattern -> !pattern.equals(frog.get("pattern")))
            .distinct()
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(wrongPatterns);
        List<String> options = new ArrayList<>(List.of(String.valueOf(frog.get("pattern"))));
        options.addAll(wrongPatterns.subList(0, 2));
        Collections.shuffle(options);
        return options;
    }

    private List<Map<String, Object>> collectedFrogs(Collection<String> collectedIds) {
        Set<String> ids = new LinkedHashSet<>(collectedIds);
        return formalFrogs().stream()
            .filter(frog -> ids.contains(frog.get("id")))
            .map(this::cardFor)
            .toList();
    }

    private Map<String, Object> challengeFor(GuardianGameMapper.GuardianRoundRow round) {
        Map<String, Object> frog = frogById(round.getFrogId());
        if (frog == null) throw new BusinessException(ErrorCode.NOT_FOUND, "守护神角色不存在");
        Map<String, Object> challenge = new LinkedHashMap<>();
        challenge.put("roundId", round.getRoundId());
        challenge.put("frogId", frog.get("id"));
        challenge.put("name", frog.get("name"));
        challenge.put("shortName", frog.get("shortName"));
        challenge.put("assetUrl", frog.get("assetUrl"));
        challenge.put("options", readOptions(round.getOptionsJson()));
        challenge.put("createdAt", round.getCreatedAt());
        return challenge;
    }

    private Map<String, Object> cardFor(Map<String, Object> frog) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", frog.get("id"));
        card.put("name", frog.get("name"));
        card.put("shortName", frog.get("shortName"));
        card.put("pattern", frog.get("pattern"));
        card.put("blessing", frog.get("blessing"));
        card.put("assetUrl", frog.get("assetUrl"));
        return card;
    }

    /** 守护神三档徽章与全站徽章目录同源（badges.json 里 condition.type=guardian 的三条）。 */
    private List<Map<String, Object>> guardianBadgeStates(int collectionCount) {
        List<Map<String, Object>> states = new ArrayList<>();
        for (BadgeCatalog.Badge badge : badgeCatalog.byConditionType("guardian")) {
            states.add(Map.of(
                "id", badge.id(),
                "name", badge.name(),
                "level", badge.level(),
                "unlocked", collectionCount >= badge.condition().count()));
        }
        return states;
    }

    private int remainingDraws(int drawsUsed, boolean shareBonusClaimed) {
        return Math.max(0, DAILY_FREE_DRAWS + (shareBonusClaimed ? SHARE_BONUS_DRAWS : 0) - drawsUsed);
    }

    private String writeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception error) {
            throw new IllegalStateException("守护神题目选项序列化失败", error);
        }
    }

    private List<String> readOptions(String optionsJson) {
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<>() { });
        } catch (Exception error) {
            throw new IllegalStateException("守护神题目选项读取失败", error);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("守护神事件结果序列化失败", error);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (Exception error) {
            throw new IllegalStateException("守护神幂等结果读取失败", error);
        }
    }
}
