package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.auth.DevSessionService;
import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.guardian.GuardianGameService;
import com.chenglei.miniprogram.quiz.ForestQuizGameService;
import com.chenglei.miniprogram.story.StoryGameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final DevSessionService sessions;
    private final StoryGameService storyGameService;
    private final GuardianGameService guardianGameService;
    private final ForestQuizGameService forestQuizGameService;
    private final ContentCatalogService content;
    private final Map<String, Map<String, Integer>> progress = new ConcurrentHashMap<>();
    private final Map<String, DevSessionService.User> profiles = new ConcurrentHashMap<>();

    public IntegrationController(DevSessionService sessions, StoryGameService storyGameService,
        GuardianGameService guardianGameService, ForestQuizGameService forestQuizGameService, ContentCatalogService content) {
        this.sessions = sessions;
        this.storyGameService = storyGameService;
        this.guardianGameService = guardianGameService;
        this.forestQuizGameService = forestQuizGameService;
        this.content = content;
    }

    @GetMapping("/home/summary")
    public ApiResponse<Map<String, Object>> home(Authentication authentication) {
        String userId = user(authentication).id();
        Map<String, Object> response = new LinkedHashMap<>(content.homeConfig());
        response.put("games", content.games());
        response.put("frogs", guardianGameService.applyUnlockState(content.frogs(), userId));
        response.put("patterns", content.patterns());
        response.put("activity", content.activities());
        return ApiResponse.success(response);
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
        DevSessionService.User user = user(authentication);
        List<Map<String, Object>> badges = guardianGameService.applyBadgeState(content.badges(), user.id());
        int frogCount = guardianGameService.progress(user.id()).get("completed") instanceof Number count ? count.intValue() : 0;
        int badgeCount = (int) badges.stream().filter(badge -> Boolean.TRUE.equals(badge.get("unlocked"))).count();
        return ApiResponse.success(Map.of("user", user, "stats", Map.of("frogs", frogCount, "frogsTotal", 9, "patterns", 0,
            "patternsTotal", content.patterns().size(), "badges", badgeCount, "badgesTotal", badges.size(), "contribution", "0.00"),
            "badges", badges, "orders", List.of()));
    }

    @PatchMapping("/me/profile")
    public ApiResponse<DevSessionService.User> updateProfile(Authentication authentication,
        @Valid @RequestBody ProfilePatch patch) {
        DevSessionService.User current = user(authentication);
        DevSessionService.User updated = new DevSessionService.User(current.id(),
            patch.nickname() == null ? current.nickname() : patch.nickname(),
            patch.avatarUrl() == null ? current.avatarUrl() : patch.avatarUrl(), false);
        profiles.put(current.id(), updated);
        return ApiResponse.success(updated);
    }

    @GetMapping("/games/{gameId}/progress")
    public ApiResponse<Map<String, Object>> gameProgress(Authentication authentication, @PathVariable String gameId) {
        ensureGame(gameId);
        if (gameId.equals("story")) return ApiResponse.success(storyGameService.progress(authentication));
        if (gameId.equals("guardian")) return ApiResponse.success(guardianGameService.progress(user(authentication).id()));
        if (gameId.equals("forest-quiz")) return ApiResponse.success(forestQuizGameService.progress(user(authentication).id()));
        int completed = progressFor(authentication, gameId).getOrDefault("completed", 0);
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
        Map<String, Integer> state = progressFor(authentication, gameId);
        int completed = "completed".equals(event.type()) || "unlocked".equals(event.type())
            ? 1 : state.getOrDefault("completed", 0);
        state.put("completed", completed);
        return ApiResponse.success(Map.of("accepted", true, "duplicated", false, "gameId", gameId,
            "progress", Map.of("gameId", gameId, "completed", completed, "total", gameId.equals("story") ? 5 : 1,
                "finished", completed > 0, "version", 1, "updatedAt", Instant.now())));
    }

    @PostMapping("/unlocks/redeem")
    public ApiResponse<Map<String, Object>> redeem(Authentication authentication, @Valid @RequestBody UnlockRequest request) {
        user(authentication);
        return ApiResponse.success(Map.of("accepted", true, "code", request.code(), "characterId", "forest",
            "characterName", "护林蛙", "message", "二维码核验成功", "redeemedAt", Instant.now()));
    }

    @GetMapping("/rankings")
    public ApiResponse<Map<String, Object>> rankings(@RequestParam(defaultValue = "total") String type) {
        String normalizedType = type.equals("weekly") ? "weekly" : "total";
        // HashMap/LinkedHashMap is intentional here: Map.of rejects the null myRank value.
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", normalizedType);
        response.put("updatedAt", Instant.now());
        response.put("items", List.of());
        response.put("myRank", null);
        response.put("page", 1);
        response.put("size", 20);
        response.put("total", 0);
        return ApiResponse.success(response);
    }

    @GetMapping("/welfare/summary")
    public ApiResponse<Map<String, Object>> welfare() {
        Map<String, Object> response = new LinkedHashMap<>(content.welfare());
        response.put("reports", List.of());
        return ApiResponse.success(response);
    }

    private DevSessionService.User user(Authentication authentication) {
        DevSessionService.User user = authentication == null ? null : (DevSessionService.User) authentication.getPrincipal();
        if (user == null) throw new IllegalStateException("未登录");
        return profiles.getOrDefault(user.id(), user);
    }

    private Map<String, Integer> progressFor(Authentication authentication, String gameId) {
        return progress.computeIfAbsent(user(authentication).id() + ":" + gameId, key -> new ConcurrentHashMap<>());
    }

    private static void ensureGame(String gameId) {
        if (!List.of("paper-cutting", "story", "guardian", "forest-quiz").contains(gameId)) throw new IllegalArgumentException("游戏不存在");
    }

    public record ProfilePatch(@Size(min = 1, max = 64) String nickname, @Size(max = 512) String avatarUrl) { }
    public record GameEvent(@NotBlank String type, Map<String, Object> payload) { }
    public record UnlockRequest(@NotBlank @Size(min = 6, max = 64) String code) { }
}
