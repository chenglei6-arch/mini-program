package com.chenglei.miniprogram.integration;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.auth.SessionService;
import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.guardian.GuardianGameService;
import com.chenglei.miniprogram.quiz.ForestQuizGameService;
import com.chenglei.miniprogram.story.StoryGameService;
import com.chenglei.miniprogram.unlock.UnlockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

/**
 * 前端契约接口。控制器只做三件事：参数校验、登录身份提取、按游戏分发到
 * 对应的服务；聚合与业务规则分别在 HomeService/ProfileService/RankingsService/
 * WelfareService 和各游戏模块的 Service 中。
 */
@RestController
@RequestMapping("/v1")
public class IntegrationController {

    private final SessionService sessions;
    private final ContentCatalogService content;
    private final GuardianGameService guardianGameService;
    private final StoryGameService storyGameService;
    private final ForestQuizGameService forestQuizGameService;
    private final UnlockService unlockService;
    private final HomeService homeService;
    private final ProfileService profileService;
    private final RankingsService rankingsService;
    private final WelfareService welfareService;

    public IntegrationController(SessionService sessions, ContentCatalogService content,
        GuardianGameService guardianGameService, StoryGameService storyGameService,
        ForestQuizGameService forestQuizGameService, UnlockService unlockService, HomeService homeService,
        ProfileService profileService, RankingsService rankingsService, WelfareService welfareService) {
        this.sessions = sessions;
        this.content = content;
        this.guardianGameService = guardianGameService;
        this.storyGameService = storyGameService;
        this.forestQuizGameService = forestQuizGameService;
        this.unlockService = unlockService;
        this.homeService = homeService;
        this.profileService = profileService;
        this.rankingsService = rankingsService;
        this.welfareService = welfareService;
    }

    @GetMapping("/home/summary")
    public ApiResponse<Map<String, Object>> home(Authentication authentication) {
        return ApiResponse.success(homeService.home(user(authentication).id()));
    }

    @GetMapping("/content/frogs")
    public ApiResponse<Map<String, Object>> frogs(Authentication authentication) {
        var frogs = guardianGameService.applyUnlockState(content.frogs(), user(authentication).id());
        return ApiResponse.success(Map.of("items", frogs, "total", frogs.size()));
    }

    @GetMapping("/content/frogs/{frogId}")
    public ApiResponse<Map<String, Object>> frogDetail(Authentication authentication, @PathVariable String frogId) {
        Map<String, Object> frog = content.frog(frogId);
        if (frog == null) throw new BusinessException(ErrorCode.NOT_FOUND, "林蛙不存在");
        return ApiResponse.success(guardianGameService.applyUnlockState(frog, user(authentication).id()));
    }

    @GetMapping("/me/profile")
    public ApiResponse<Map<String, Object>> profile(Authentication authentication) {
        return ApiResponse.success(profileService.profile(user(authentication).id()));
    }

    @PatchMapping("/me/profile")
    public ApiResponse<CurrentUser> updateProfile(Authentication authentication,
        @Valid @RequestBody ProfilePatch patch) {
        return ApiResponse.success(profileService.updateProfile(user(authentication).id(),
            patch.nickname(), patch.avatarUrl()));
    }

    @GetMapping("/games/{gameId}/progress")
    public ApiResponse<Map<String, Object>> gameProgress(Authentication authentication,
        @PathVariable String gameId) {
        return ApiResponse.success(switch (gameId) {
            case "story" -> storyGameService.progress(authentication);
            case "guardian" -> guardianGameService.progress(user(authentication).id());
            case "forest-quiz" -> forestQuizGameService.progress(user(authentication).id());
            default -> throw new BusinessException(ErrorCode.NOT_FOUND, "游戏不存在");
        });
    }

    @PostMapping("/games/{gameId}/events")
    public ApiResponse<Map<String, Object>> gameEvent(Authentication authentication, @PathVariable String gameId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody GameEvent event) {
        return ApiResponse.success(switch (gameId) {
            case "story" -> storyEvent(authentication, idempotencyKey, event);
            case "guardian" -> guardianEvent(user(authentication).id(), idempotencyKey, event);
            case "forest-quiz" -> quizEvent(user(authentication).id(), idempotencyKey, event);
            default -> throw new BusinessException(ErrorCode.NOT_FOUND, "游戏不存在");
        });
    }

    @PostMapping("/unlocks/redeem")
    public ApiResponse<Map<String, Object>> redeem(Authentication authentication, @Valid @RequestBody UnlockRequest request) {
        return ApiResponse.success(unlockService.redeem(user(authentication).id(), request.code()));
    }

    @GetMapping("/rankings")
    public ApiResponse<Map<String, Object>> rankings(Authentication authentication,
        @RequestParam(defaultValue = "total") String type) {
        return ApiResponse.success(rankingsService.rankings(type, user(authentication)));
    }

    @GetMapping("/welfare/summary")
    public ApiResponse<Map<String, Object>> welfare() {
        return ApiResponse.success(welfareService.summary());
    }

    private Map<String, Object> storyEvent(Authentication authentication, String idempotencyKey, GameEvent event) {
        if (event.type().equals("reset")) return storyGameService.reset(authentication);
        if (!event.type().equals("story_choice") || event.payload() == null
            || !(event.payload().get("choiceId") instanceof String choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "故事事件必须包含 choiceId");
        }
        return storyGameService.choose(authentication, idempotencyKey, choiceId);
    }

    private Map<String, Object> guardianEvent(String userId, String idempotencyKey, GameEvent event) {
        if (event.type().equals("draw")) return guardianGameService.draw(userId, idempotencyKey);
        if (event.type().equals("share")) return guardianGameService.claimShareBonus(userId, idempotencyKey);
        if (event.type().equals("answer") && event.payload() != null
            && event.payload().get("roundId") instanceof String roundId
            && event.payload().get("pattern") instanceof String pattern) {
            return guardianGameService.answer(userId, idempotencyKey, roundId, pattern);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "守护神事件仅支持 draw、answer 或 share；answer 必须包含 roundId 和 pattern");
    }

    private Map<String, Object> quizEvent(String userId, String idempotencyKey, GameEvent event) {
        if (event.type().equals("quiz_answer") && event.payload() != null
            && event.payload().get("levelId") instanceof String levelId
            && event.payload().get("questionId") instanceof String questionId
            && event.payload().get("optionId") instanceof String optionId) {
            return forestQuizGameService.answer(userId, idempotencyKey, levelId, questionId, optionId);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "知识闯关事件必须包含 levelId、questionId 和 optionId");
    }

    private CurrentUser user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user;
    }

    public record ProfilePatch(@Size(min = 1, max = 64) String nickname, @Size(max = 512) String avatarUrl) { }
    public record GameEvent(@NotBlank String type, Map<String, Object> payload) { }
    public record UnlockRequest(@NotBlank @Size(min = 6, max = 64) String code) { }
}
