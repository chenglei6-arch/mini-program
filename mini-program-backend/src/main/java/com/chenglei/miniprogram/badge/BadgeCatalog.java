package com.chenglei.miniprogram.badge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 徽章目录的唯一来源：id、名称、等级与解锁条件全部来自 classpath 的 badges.json。
 * 个人中心、守护神进度与闯关关卡都从这里取徽章信息，不再各自维护一份。
 */
@Component
public class BadgeCatalog {

    /** count 用于数量型条件，levelId 用于闯关关卡型条件。 */
    public record Condition(String type, Integer count, String levelId) { }

    public record Badge(String id, String name, String level, Condition condition) { }

    private final List<Badge> badges;
    private final Map<String, Badge> byId;

    public BadgeCatalog(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("badges.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            List<Badge> loaded = new ArrayList<>();
            Map<String, Badge> index = new LinkedHashMap<>();
            for (JsonNode node : root.path("badges")) {
                JsonNode condition = node.path("condition");
                Badge badge = new Badge(
                    node.path("id").asText(),
                    node.path("name").asText(),
                    node.path("level").asText(),
                    new Condition(
                        condition.path("type").asText(),
                        condition.hasNonNull("count") ? condition.get("count").asInt() : null,
                        condition.hasNonNull("levelId") ? condition.get("levelId").asText() : null));
                loaded.add(badge);
                index.put(badge.id(), badge);
            }
            if (loaded.isEmpty()) throw new IllegalStateException("徽章目录为空");
            this.badges = List.copyOf(loaded);
            this.byId = Map.copyOf(index);
        } catch (Exception error) {
            throw new IllegalStateException("徽章目录加载失败", error);
        }
    }

    public List<Badge> all() {
        return badges;
    }

    /** 按解锁条件类型取子目录，供同口径的模块复用（如守护神的 1/3/9 三档）。 */
    public List<Badge> byConditionType(String type) {
        return badges.stream().filter(badge -> badge.condition().type().equals(type)).toList();
    }

    /** 按 id 取徽章；目录里没有该 id 说明调用方写错了，直接抛错。 */
    public Badge get(String badgeId) {
        Badge badge = byId.get(badgeId);
        if (badge == null) throw new IllegalStateException("未知徽章：" + badgeId);
        return badge;
    }

    public String nameOf(String badgeId) {
        return get(badgeId).name();
    }
}
