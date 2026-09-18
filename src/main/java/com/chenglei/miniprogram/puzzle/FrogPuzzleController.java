package com.chenglei.miniprogram.puzzle;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指尖剪林蛙接口。只做参数校验、登录身份提取与响应包装，
 * 组件目录与进度业务在 {@link FrogPuzzleService}。
 */
@RestController
@RequestMapping("/v1/games/frog-puzzle")
public class FrogPuzzleController {

    private final FrogPuzzleService puzzleService;

    public FrogPuzzleController(FrogPuzzleService puzzleService) {
        this.puzzleService = puzzleService;
    }

    @GetMapping("/frogs/{frogId}/components")
    public ApiResponse<Map<String, Object>> getFrogComponents(Authentication authentication,
        @PathVariable String frogId) {
        getUser(authentication);
        return ApiResponse.success(puzzleService.componentsFor(frogId));
    }

    @PostMapping("/events")
    public ApiResponse<Map<String, Object>> handleEvent(Authentication authentication,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody PuzzleEvent event) {
        String userId = getUser(authentication).id();
        return ApiResponse.success(puzzleService.handleEvent(userId, idempotencyKey, event.type(), event.payload()));
    }

    @GetMapping("/progress")
    public ApiResponse<Map<String, Object>> getProgress(Authentication authentication) {
        String userId = getUser(authentication).id();
        return ApiResponse.success(puzzleService.progress(userId));
    }

    private CurrentUser getUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return (CurrentUser) authentication.getPrincipal();
    }

    public record PuzzleEvent(
        @NotBlank String type,
        FrogPuzzleService.PuzzleEventPayload payload
    ) {}
}
