package com.chenglei.miniprogram.puzzle;

import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.common.storage.GameProgressStore;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指尖剪林蛙（点击式拼装）的组件目录与进度业务。
 *
 * 组件清单来自 classpath 的 frog-components.json；进度持久化在 game_progress，
 * 每次完成蛙数变化后同步徽章流水。当前组件解锁规则为"全部解锁"（测试约定），
 * 后续接入纹样收集后再收紧。
 */
@Service
public class FrogPuzzleService {

    public static final String GAME_ID = "frog-puzzle";

    private final GameProgressStore store;
    private final BadgeService badgeService;
    private final FrogPuzzleStateJson stateJson;
    private final ObjectMapper objectMapper;
    /** 全部组件 ID：当前解锁规则为"全部解锁"，与用户无关，启动时算一次。 */
    private Set<String> allComponentIds = Set.of();
    private Map<String, FrogComponentData> frogComponents;

    public FrogPuzzleService(GameProgressStore store, BadgeService badgeService, FrogPuzzleStateJson stateJson,
        ObjectMapper objectMapper) {
        this.store = store;
        this.badgeService = badgeService;
        this.stateJson = stateJson;
        this.objectMapper = objectMapper;
        loadComponents();
    }

    public Map<String, Object> componentsFor(String frogId) {
        FrogComponentData frogData = frogComponents.get(frogId);
        if (frogData == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "青蛙不存在");
        }
        List<Map<String, Object>> components = new ArrayList<>();
        for (ComponentInfo comp : frogData.components()) {
            Map<String, Object> componentMap = new HashMap<>();
            componentMap.put("id", comp.id());
            componentMap.put("name", comp.name());
            componentMap.put("assetUrl", comp.assetUrl());
            componentMap.put("order", comp.order());
            componentMap.put("unlocked", allComponentIds.contains(comp.id()));
            components.add(componentMap);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("frogId", frogId);
        response.put("frogName", frogData.name());
        response.put("components", components);
        response.put("totalComponents", components.size());
        return response;
    }

    @Transactional
    public Map<String, Object> handleEvent(String userId, String idempotencyKey, String eventType,
        PuzzleEventPayload payload) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()
            && store.findEventPayload(userId, GAME_ID, idempotencyKey) != null) {
            return duplicatedResult();
        }
        if (!"frog_completed".equals(eventType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的事件类型");
        }
        return handleFrogComplete(userId, payload, idempotencyKey);
    }

    public Map<String, Object> progress(String userId) {
        FrogPuzzleService.PuzzleProgress progress = stateJson.read(store.loadState(userId, GAME_ID));
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("completedFrogs", progress.completedFrogs);
        response.put("totalPoints", progress.totalPoints);
        response.put("totalFrogs", frogComponents.size());
        response.put("updatedAt", Instant.now());
        return response;
    }

    private Map<String, Object> handleFrogComplete(String userId, PuzzleEventPayload payload, String idempotencyKey) {
        if (payload == null || payload.frogId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少青蛙ID");
        }
        String frogId = payload.frogId();
        FrogComponentData frogData = frogComponents.get(frogId);
        if (frogData == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "青蛙不存在");
        }

        // 行锁读取或初始化持久化进度，保证同一用户并发事件串行化
        FrogPuzzleService.PuzzleProgress progress = stateJson.read(store.loadStateForUpdate(userId, GAME_ID));
        if (!progress.completedFrogs.contains(frogId)) {
            progress.completedFrogs.add(frogId);
            progress.totalPoints += 10; // 每只青蛙10分
        }
        store.saveState(userId, GAME_ID, stateJson.write(progress), isAllFrogsCompleted(progress));
        // 完成蛙数变化后同步徽章流水（剪纸新手/匠人/大师）。
        badgeService.evaluate(userId);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            store.recordEvent(userId, GAME_ID, idempotencyKey, "frog_completed", stateJson.frogPayload(frogId));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("frogId", frogId);
        response.put("frogName", frogData.name());
        response.put("points", 10);
        response.put("totalPoints", progress.totalPoints);
        response.put("completedFrogs", progress.completedFrogs.size());
        response.put("timestamp", Instant.now());
        return response;
    }

    private Map<String, Object> duplicatedResult() {
        return Map.of("success", true, "duplicated", true, "message", "事件已处理");
    }

    private boolean isAllFrogsCompleted(FrogPuzzleService.PuzzleProgress progress) {
        return frogComponents != null && !frogComponents.isEmpty()
            && progress.completedFrogs.size() >= frogComponents.size();
    }

    /** 测试约定：所有组件默认全部解锁。 */
    private void loadComponents() {
        try {
            ClassPathResource resource = new ClassPathResource("frog-components.json");
            Map<String, Object> data = objectMapper.readValue(resource.getInputStream(), Map.class);
            List<Map<String, Object>> frogs = (List<Map<String, Object>>) data.get("frogs");

            frogComponents = new HashMap<>();
            Set<String> componentIds = new HashSet<>();
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
                    componentIds.add((String) comp.get("id"));
                }
                frogComponents.put(frogId, new FrogComponentData(frogId, name, componentList));
            }
            allComponentIds = componentIds;
        } catch (IOException e) {
            // 与其他资源加载保持一致：组件清单缺失应让启动失败，而不是让所有接口
            // 静默返回"青蛙不存在"。
            throw new IllegalStateException("拼图组件加载失败", e);
        }
    }

    // 拼图进度：经 FrogPuzzleStateJson 与 game_progress 的 progress_json 互转
    @JsonAutoDetect(creatorVisibility = JsonAutoDetect.Visibility.ANY,
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
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

    public record PuzzleEventPayload(
        String frogId,
        Integer timeCost,
        List<String> components
    ) {}
}
