package com.chenglei.miniprogram.puzzle;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.common.api.ApiResponse;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.common.storage.GameProgressStore;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/games/frog-puzzle")
public class FrogPuzzleController {

    static final String GAME_ID = "frog-puzzle";

    private final ObjectMapper objectMapper = new ObjectMapper()
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    private final GameProgressStore store;
    private final BadgeService badgeService;
    private final Map<String, Set<String>> userComponents = new ConcurrentHashMap<>();
    private Map<String, FrogComponentData> frogComponents;

    public FrogPuzzleController(GameProgressStore store, BadgeService badgeService) {
        this.store = store;
        this.badgeService = badgeService;
        loadComponents();
    }

    @SuppressWarnings("unchecked")
    private void loadComponents() {
        try {
            ClassPathResource resource = new ClassPathResource("frog-components.json");
            Map<String, Object> data = objectMapper.readValue(resource.getInputStream(), Map.class);
            List<Map<String, Object>> frogs = (List<Map<String, Object>>) data.get("frogs");

            frogComponents = new HashMap<>();
            for (Map<String, Object> frog : frogs) {
                String frogId = (String) frog.get("frogId");
                String name = (String) frog.get("name");
                List<Map<String, Object>> components = (List<Map<String, Object>>) frog.get("components");

                List<ComponentInfo> componentList = new ArrayList<>();
                for (Map<String, Object> comp : components) {
                    componentList.add(new ComponentInfo(
                        (String) comp.get("id"),
                        (String) comp.get("name"),
                        (String) comp.get("assetUrl"),
                        ((Number) comp.get("order")).intValue()
                    ));
                }

                frogComponents.put(frogId, new FrogComponentData(frogId, name, componentList));
            }
        } catch (IOException e) {
            frogComponents = new HashMap<>();
        }
    }

    @GetMapping("/frogs/{frogId}/components")
    public ApiResponse<Map<String, Object>> getFrogComponents(
        Authentication authentication,
        @PathVariable String frogId
    ) {
        String userId = getUser(authentication).id();

        FrogComponentData frogData = frogComponents.get(frogId);
        if (frogData == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "青蛙不存在");
        }

        // 获取用户已解锁的组件（测试环境默认全部解锁）
        Set<String> unlockedComponents = getUserComponents(userId);

        // 应用解锁状态
        List<Map<String, Object>> components = new ArrayList<>();
        for (ComponentInfo comp : frogData.components()) {
            Map<String, Object> componentMap = new HashMap<>();
            componentMap.put("id", comp.id());
            componentMap.put("name", comp.name());
            componentMap.put("assetUrl", comp.assetUrl());
            componentMap.put("order", comp.order());
            componentMap.put("unlocked", unlockedComponents.contains(comp.id()));
            components.add(componentMap);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("frogId", frogId);
        response.put("frogName", frogData.name());
        response.put("components", components);
        response.put("totalComponents", components.size());

        return ApiResponse.success(response);
    }

    @GetMapping("/user/components")
    public ApiResponse<Map<String, Object>> getUserUnlockedComponents(Authentication authentication) {
        String userId = getUser(authentication).id();
        Set<String> unlocked = getUserComponents(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("components", unlocked);
        response.put("total", unlocked.size());

        return ApiResponse.success(response);
    }

    @PostMapping("/events")
    @Transactional
    public ApiResponse<Map<String, Object>> handleEvent(
        Authentication authentication,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody PuzzleEvent event
    ) {
        String userId = getUser(authentication).id();

        if (idempotencyKey != null && !idempotencyKey.isBlank()
            && store.findEventPayload(userId, GAME_ID, idempotencyKey) != null) {
            return ApiResponse.success(Map.of(
                "success", true,
                "duplicated", true,
                "message", "事件已处理"
            ));
        }

        if ("frog_completed".equals(event.type())) {
            return handleFrogComplete(userId, event, idempotencyKey);
        }

        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的事件类型");
    }

    private ApiResponse<Map<String, Object>> handleFrogComplete(
        String userId,
        PuzzleEvent event,
        String idempotencyKey
    ) {
        if (event.payload() == null || event.payload().frogId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少青蛙ID");
        }

        String frogId = event.payload().frogId();

        // 验证青蛙存在
        FrogComponentData frogData = frogComponents.get(frogId);
        if (frogData == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "青蛙不存在");
        }

        // 读取或初始化持久化进度（行锁保证同一用户并发事件串行化）
        PuzzleProgress progress = loadProgress(userId);

        // 记录完成的青蛙
        if (!progress.completedFrogs.contains(frogId)) {
            progress.completedFrogs.add(frogId);
            progress.totalPoints += 10; // 每只青蛙10分
        }
        saveProgress(userId, progress);
        // 完成蛙数变化后同步徽章流水（剪纸新手/匠人/大师）。
        badgeService.evaluate(userId);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            store.recordEvent(userId, GAME_ID, idempotencyKey, "frog_completed", payloadJson(frogId));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("frogId", frogId);
        response.put("frogName", frogData.name());
        response.put("points", 10);
        response.put("totalPoints", progress.totalPoints);
        response.put("completedFrogs", progress.completedFrogs.size());
        response.put("timestamp", Instant.now());

        return ApiResponse.success(response);
    }

    @GetMapping("/progress")
    public ApiResponse<Map<String, Object>> getProgress(Authentication authentication) {
        String userId = getUser(authentication).id();
        PuzzleProgress progress = readProgress(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("completedFrogs", progress.completedFrogs);
        response.put("totalPoints", progress.totalPoints);
        response.put("totalFrogs", frogComponents.size());
        response.put("updatedAt", Instant.now());

        return ApiResponse.success(response);
    }

    private PuzzleProgress readProgress(String userId) {
        String json = store.loadState(userId, GAME_ID);
        return parseProgress(json);
    }

    private PuzzleProgress loadProgress(String userId) {
        String json = store.loadStateForUpdate(userId, GAME_ID);
        return parseProgress(json);
    }

    private PuzzleProgress parseProgress(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return new PuzzleProgress();
        try {
            return objectMapper.readValue(json, PuzzleProgress.class);
        } catch (IOException e) {
            throw new IllegalStateException("拼图进度反序列化失败", e);
        }
    }

    private void saveProgress(String userId, PuzzleProgress progress) {
        try {
            store.saveState(userId, GAME_ID, objectMapper.writeValueAsString(progress),
                frogComponents != null && !frogComponents.isEmpty()
                    && progress.completedFrogs.size() >= frogComponents.size());
        } catch (IOException e) {
            throw new IllegalStateException("拼图进度序列化失败", e);
        }
    }

    private String payloadJson(String frogId) {
        try {
            return objectMapper.writeValueAsString(Map.of("frogId", frogId));
        } catch (IOException e) {
            return "{}";
        }
    }

    // 获取用户已解锁的组件（测试环境：所有组件默认解锁）
    private Set<String> getUserComponents(String userId) {
        return userComponents.computeIfAbsent(userId, k -> {
            Set<String> allComponents = new HashSet<>();
            for (FrogComponentData frog : frogComponents.values()) {
                for (ComponentInfo comp : frog.components()) {
                    allComponents.add(comp.id());
                }
            }
            return allComponents;
        });
    }

    private CurrentUser getUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return (CurrentUser) authentication.getPrincipal();
    }

    // 内部类：按字段与 game_progress 的 progress_json 互转
    static class PuzzleProgress {
        List<String> completedFrogs = new ArrayList<>();
        int totalPoints = 0;
    }

    record FrogComponentData(
        String frogId,
        String name,
        List<ComponentInfo> components
    ) {}

    record ComponentInfo(
        String id,
        String name,
        String assetUrl,
        int order
    ) {}

    // 请求体
    public record PuzzleEvent(
        @NotBlank String type,
        PuzzleEventPayload payload
    ) {}

    public record PuzzleEventPayload(
        String frogId,
        Integer timeCost,
        List<String> components
    ) {}
}
