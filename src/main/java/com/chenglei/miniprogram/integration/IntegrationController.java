package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.auth.DevSessionService;
import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.story.StoryGameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
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

    private static final List<Map<String, Object>> GAMES = List.of(
        Map.of("id", "paper-cutting", "title", "指尖剪林蛙", "subtitle", "拼一张剪纸，认识一组纹样", "path", "/pages/games/paper-cutting/paper-cutting"),
        Map.of("id", "story", "title", "林蛙谷寻踪", "subtitle", "听一段说部，走完一段旅程", "path", "/pages/games/story/story"),
        Map.of("id", "guardian", "title", "摇一摇·守护神", "subtitle", "解锁你的林蛙守护神", "path", "/pages/games/guardian/guardian")
    );
    private static final List<String> PATTERNS = List.of("蛙纹", "山林纹", "水波纹", "云纹", "花叶纹");
    private static final List<Map<String, Object>> FROGS = List.of(
        frog("forest", "护林蛙", "护", "山林纹", true), frog("ginseng", "采参蛙", "采", "云纹", false),
        frog("hibernation", "冬眠蛙", "冬", "水波纹", false), frog("lotus", "荷叶蛙", "荷", "蛙纹", false),
        frog("insect", "捕虫蛙", "捕", "虫鸟纹", false), frog("immune", "免疫蛙", "免", "太阳纹", false),
        frog("youth", "驻颜蛙", "驻", "花叶纹", false), frog("snow", "天池映雪蛙", "雪", "冰雪纹", false),
        frog("lung", "润肺蛙", "润", "空气纹", false)
    );
    private static final List<Map<String, Object>> BADGES = List.of(
        Map.of("id", "paper-beginner", "name", "剪纸新手", "level", "bronze", "unlocked", true),
        Map.of("id", "story-listener", "name", "故事聆听者", "level", "bronze", "unlocked", false),
        Map.of("id", "guardian-first", "name", "守护神初遇", "level", "bronze", "unlocked", false)
    );
    private final DevSessionService sessions;
    private final StoryGameService storyGameService;
    private final Map<String, Map<String, Integer>> progress = new ConcurrentHashMap<>();
    private final Map<String, DevSessionService.User> profiles = new ConcurrentHashMap<>();

    public IntegrationController(DevSessionService sessions, StoryGameService storyGameService) {
        this.sessions = sessions;
        this.storyGameService = storyGameService;
    }

    @GetMapping("/home/summary")
    public ApiResponse<Map<String, Object>> home() {
        return ApiResponse.success(Map.of("brand", "纸韵蛙鸣·哈什蚂传奇", "slogan", "拼一张剪纸，听一段说部",
            "fundAmount", "12,480.00", "fundUpdateAt", "2026-08-17 12:00", "games", GAMES, "frogs", FROGS,
            "patterns", PATTERNS, "activity", List.of("吉林·张同学刚刚集齐了第6枚徽章", "辽宁·李同学解锁了蛙纹", "黑龙江·王同学完成了林蛙谷第1章")));
    }

    @GetMapping("/me/profile")
    public ApiResponse<Map<String, Object>> profile(Authentication authentication) {
        DevSessionService.User user = user(authentication);
        List<Map<String, Object>> badges = BADGES;
        return ApiResponse.success(Map.of("user", user, "stats", Map.of("frogs", 1, "frogsTotal", 9, "patterns", 1,
            "patternsTotal", 5, "badges", 1, "badgesTotal", 9, "contribution", "1.00"), "badges", badges, "orders", List.of()));
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
        if (!type.equals("total") && !type.equals("weekly")) {
            return ApiResponse.success(Map.of("type", "total", "updatedAt", Instant.now(), "items", List.of(),
                "myRank", null, "page", 1, "size", 20, "total", 0));
        }
        return ApiResponse.success(Map.of("type", type, "updatedAt", Instant.now(), "items", List.of(),
            "myRank", null, "page", 1, "size", 20, "total", 0));
    }

    @GetMapping("/welfare/summary")
    public ApiResponse<Map<String, Object>> welfare() {
        return ApiResponse.success(Map.of("fundAmount", "12,480.00", "reports", List.of(), "updatedAt", "2026-08-17"));
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
        if (!List.of("paper-cutting", "story", "guardian").contains(gameId)) throw new IllegalArgumentException("游戏不存在");
    }

    private static Map<String, Object> frog(String id, String name, String shortName, String pattern, boolean unlocked) {
        return Map.of("id", id, "name", name, "shortName", shortName, "pattern", pattern, "unlocked", unlocked);
    }

    public record ProfilePatch(@Size(min = 1, max = 64) String nickname, @Size(max = 512) String avatarUrl) { }
    public record GameEvent(@NotBlank String type, Map<String, Object> payload) { }
    public record UnlockRequest(@NotBlank @Size(min = 6, max = 64) String code) { }
}
