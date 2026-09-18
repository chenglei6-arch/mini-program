package com.chenglei.miniprogram.story;

import com.chenglei.miniprogram.auth.CurrentUser;
import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.chenglei.miniprogram.common.storage.GameProgressStore;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 《哈什玛灵纹之书》的服务端状态机。
 *
 * 文字内容和选择规则由服务端决定，客户端只提交 choiceId，避免把结局条件
 * 和数值判定暴露成可被客户端直接篡改的逻辑。完整状态经 {@link StoryStateJson}
 * 序列化后落在 game_progress 表，事件幂等走 game_event。
 * 场景正文只来自 story-content.json 的段落目录，状态里不再存一份短文本。
 */
@Service
public class StoryGameService {

    private static final String GAME_ID = "story";
    private static final List<String> ROUTES = List.of("A", "B", "C");
    private static final Map<String, String> ROUTE_NAMES = Map.of(
        "A", "红松松岗 · 《山林规约卷》",
        "B", "鸭绿江湿地 · 《水泽灵物卷》",
        "C", "天池冰渊 · 《冰雪传说卷》"
    );

    private final GameProgressStore store;
    private final StoryContentCatalog content;
    private final BadgeService badgeService;
    private final StoryStateJson stateJson;

    public StoryGameService(GameProgressStore store, StoryContentCatalog content, BadgeService badgeService,
        StoryStateJson stateJson) {
        this.store = store;
        this.content = content;
        this.badgeService = badgeService;
        this.stateJson = stateJson;
    }

    public Map<String, Object> progress(Authentication authentication) {
        return state(userId(authentication)).toResponse(content);
    }

    @Transactional
    public Map<String, Object> reset(Authentication authentication) {
        String userId = userId(authentication);
        store.deleteState(userId, GAME_ID);
        return new StoryState().toResponse(content);
    }

    @Transactional
    public Map<String, Object> choose(Authentication authentication, String idempotencyKey, String choiceId) {
        String userId = userId(authentication);
        if (choiceId == null || choiceId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少故事选择");
        }
        StoryState state = lockedState(userId);
        // 幂等判定放在行锁之后：并发同键请求串行化，先到的落库，后到的直接复用结果，
        // 不会出现两边都通过前置检查后重复推进状态。
        boolean dedupe = idempotencyKey != null && !idempotencyKey.isBlank();
        if (dedupe) {
            String previousChoice = stateJson.choiceOf(store.findEventPayload(userId, GAME_ID, idempotencyKey));
            if (previousChoice != null) {
                if (!previousChoice.equals(choiceId)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "幂等键已经用于其他故事选择");
                }
                return state.toResponse(content);
            }
        }

        applyChoice(state, choiceId);
        store.saveState(userId, GAME_ID, stateJson.write(state), state.finished);
        if (dedupe) {
            store.recordEvent(userId, GAME_ID, idempotencyKey, "story_choice", stateJson.choicePayload(choiceId));
        }
        // 每次选择后同步徽章流水（故事聆听者/说部传承人在路线完成时解锁）。
        badgeService.evaluate(userId);
        return state.toResponse(content);
    }

    private StoryState state(String userId) {
        String json = store.loadState(userId, GAME_ID);
        return json == null ? new StoryState() : stateJson.read(json);
    }

    private StoryState lockedState(String userId) {
        String json = store.loadStateForUpdate(userId, GAME_ID);
        return json == null ? new StoryState() : stateJson.read(json);
    }

    private void applyChoice(StoryState state, String choiceId) {
        if ("reset".equals(choiceId)) {
            state.reset();
            return;
        }
        if (state.gameOver || state.finished) {
            throw new BusinessException(ErrorCode.CONFLICT, "本局故事已经结束，请重新开始");
        }

        switch (state.sceneId) {
            case "intro" -> chooseRoute(state, choiceId);
            case "soul" -> chooseSoul(state, choiceId);
            case "order" -> chooseOrderEvent(state, choiceId);
            case "encounter-1" -> chooseEncounterOne(state, choiceId);
            case "encounter-2" -> chooseEncounterTwo(state, choiceId);
            case "guardian" -> chooseGuardian(state, choiceId);
            case "propagation" -> choosePropagation(state, choiceId);
            case "chat" -> finishRoute(state, choiceId);
            case "vision" -> chooseVision(state, choiceId);
            case "hub" -> chooseHub(state, choiceId);
            case "finale" -> finishStory(state, choiceId);
            case "early-exit" -> finishStory(state, "early-exit");
            default -> throw new BusinessException(ErrorCode.CONFLICT, "当前故事场景不可选择");
        }
    }

    private void chooseRoute(StoryState state, String choiceId) {
        if (!ROUTES.contains(choiceId) || state.completedRoutes.contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择尚未完成的路线");
        }
        state.currentRoute = choiceId;
        state.routeOrder.add(choiceId);
        enterRoute(state);
    }

    private void enterRoute(StoryState state) {
        if (!state.soulResolved) {
            state.sceneId = "soul";
            state.sceneTitle = "支线 S · 迷途寻访者亡魂";
            state.speaker = "旁白";
            state.choices = choices(
                choice("S-1", "倾听遗憾，举行简易超度仪式", "以悲悯回应亡魂"),
                choice("S-2", "无视亡魂，继续赶路", "不改变数值与背包"),
                choice("S-3", "威逼残魂，夺取遗物", "获得手记，但会增加私欲")
            );
            return;
        }
        if (hasOrderEvent(state)) {
            state.sceneId = "order";
            state.sceneTitle = "行进顺序留下的回声";
            state.speaker = "旁白";
            state.choices = orderChoices(state);
        } else {
            showEncounterOne(state);
        }
    }

    private void chooseSoul(StoryState state, String choiceId) {
        if (!Set.of("S-1", "S-2", "S-3").contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的亡魂选择");
        }
        state.soulResolved = true;
        switch (choiceId) {
            case "S-1" -> {
                addItem(state, "旧寻访手记");
                addItem(state, "破碎萨满神牌");
                state.notes.add("迷途寻访者的遗憾");
            }
            case "S-3" -> {
                state.desire += 2;
                addItem(state, "旧寻访手记");
            }
            default -> { }
        }
        continueAfterSoul(state);
    }

    private void continueAfterSoul(StoryState state) {
        state.sceneId = "order";
        state.sceneTitle = "亡魂的回声散去";
        state.speaker = "旁白";
        state.choices = choices(choice("continue", "收好行囊，继续前行", "前往当前路线"));
    }

    private void chooseOrderEvent(StoryState state, String choiceId) {
        if ("continue".equals(choiceId)) {
            showEncounterOne(state);
            return;
        }
        // 亡魂支线结束后的 order 场景在首条路线没有顺序事件；
        // 非法选项不能落到 routeOrder 的 -2 下标上。
        if (!hasOrderEvent(state)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的行进选择");
        }
        String previous = state.routeOrder.get(state.routeOrder.size() - 2);
        String expectedPrefix = state.currentRoute + "-S-";
        Set<String> validChoices = previous.equals("C") && state.currentRoute.equals("A")
            ? Set.of("A-S-A", "A-S-B", "A-S-C")
            : previous.equals("A") && state.currentRoute.equals("B")
                ? Set.of("B-S-A", "B-S-B", "B-S-C")
                : Set.of("C-S-A", "C-S-B", "C-S-C", "C-S-D");
        if (!choiceId.startsWith(expectedPrefix) || !validChoices.contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的行进选择");
        }
        if (choiceId.equals("A-S-A") || choiceId.equals("B-S-A")) {
            state.notes.add(choiceId.startsWith("A") ? "冰雪残风侵林" : "水泽传训");
        } else if (choiceId.equals("C-S-D")) {
            state.desire += 1;
        }
        showEncounterOne(state);
    }

    private void showEncounterOne(StoryState state) {
        String route = state.currentRoute;
        state.sceneId = "encounter-1";
        state.sceneTitle = route.equals("A") ? "废弃猎户营地" : route.equals("B") ? "水神小石祠" : "祭冰石台";
        state.speaker = "旁白";
        state.choices = route.equals("A")
            ? choices(choice("A-a-1", "完整探查营地", "记录铜牌与陶片"), choice("A-a-2", "摘抄核心铭文", "只留下简版札记"), choice("A-a-3", "轻声缅怀后离开", "不获得札记"), choice("A-a-4", "完全略过", "不改变主线"))
            : route.equals("B")
                ? choices(choice("B-a-1", "研读石刻并拾取残牌", "记录完整祷文"), choice("B-a-2", "粗略查看纹样", "获得简版札记"), choice("B-a-3", "直接略过", "不改变主线"))
                : choices(choice("C-a-1", "完整观察并摘抄祝辞", "记录冬日祭冰纹"), choice("C-a-2", "摘抄片段祝辞", "获得简版札记"), choice("C-a-3", "无视遗迹", "不改变主线"));
    }

    private void chooseEncounterOne(StoryState state, String choiceId) {
        if (!validEncounterOneChoice(state.currentRoute, choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的遗迹选择");
        }
        if (choiceId.endsWith("1")) state.notes.add(noteFor(state.currentRoute, false));
        if (choiceId.endsWith("2")) state.notes.add(noteFor(state.currentRoute, true));
        showEncounterTwo(state);
    }

    private void showEncounterTwo(StoryState state) {
        String route = state.currentRoute;
        state.sceneId = "encounter-2";
        state.sceneTitle = route.equals("A") ? "年迈老萨满" : route.equals("B") ? "湿地渡口商人" : "冰缘残破抄本";
        state.speaker = route.equals("A") ? "老萨满" : route.equals("B") ? "外来商人" : "旁白";
        state.choices = route.equals("A")
            ? choices(choice("A-b-1", "恭敬行礼，耐心聆听", "获得传承人嘱托竹简"), choice("A-b-2", "急切追问古卷线索", "仍会获得竹简"), choice("A-b-3", "沉默听他说完", "仍会获得竹简"))
            : route.equals("B")
                ? choices(choice("B-b-1", "义正言辞反驳商人", "拒绝交易"), choice("B-b-2", "沉默接过密信", "保留交易可能"))
                : choices(choice("C-b-1", "捡起残本仔细阅读", "获得残本天池谣"), choice("C-b-2", "看一眼便路过", "不获得札记"));
    }

    private void chooseEncounterTwo(StoryState state, String choiceId) {
        String route = state.currentRoute;
        if (!validEncounterTwoChoice(route, choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的传承选择");
        }
        if (route.equals("A")) {
            addItem(state, "传承人嘱托竹简");
            state.notes.add("传承人的双重遗愿");
        } else if (route.equals("B") && choiceId.equals("B-b-2")) {
            addItem(state, "外来商人密信");
        } else if (route.equals("C") && choiceId.equals("C-b-1")) {
            state.notes.add("残本天池谣");
        }
        showGuardian(state);
    }

    private void showGuardian(StoryState state) {
        String route = state.currentRoute;
        state.sceneId = "guardian";
        state.sceneTitle = route.equals("A") ? "护林蛙 · 山林规约卷" : route.equals("B") ? "荷叶蛙 · 水泽灵物卷" : "天池映雪蛙 · 冰雪传说卷";
        state.speaker = route.equals("A") ? "护林蛙" : route.equals("B") ? "荷叶蛙" : "天池映雪蛙";
        state.choices = choices(
            choice(route + "1", "取走实体古卷", "古卷离开原生环境，敬畏值下降"),
            choice(route + "2", "留在原地抄录副本", "本体留在故土，敬畏值上升")
        );
    }

    private void chooseGuardian(StoryState state, String choiceId) {
        String route = state.currentRoute;
        if (!Set.of(route + "1", route + "2").contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的古卷选择");
        }
        boolean entity = choiceId.endsWith("1");
        state.routeRecords.put(route, entity ? "entity" : "copy");
        if (entity) {
            state.reverence += route.equals("C") ? -3 : -2;
            if (route.equals("B") && state.inventory.contains("外来商人密信")) state.desire += 1;
        } else {
            state.reverence += route.equals("C") ? 3 : 2;
        }
        state.sceneId = "propagation";
        state.sceneTitle = "古卷的传播方式";
        state.speaker = "灵蛙";
        state.choices = choices(
            choice(route + "a", "恪守原文，不作通俗改写", "传播开放值 -2"),
            choice(route + "b", "保留内核，允许适度改写", "传播开放值 +2")
        );
    }

    private void choosePropagation(StoryState state, String choiceId) {
        if (!Set.of(state.currentRoute + "a", state.currentRoute + "b").contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的传播选择");
        }
        boolean open = choiceId.endsWith("b");
        state.openness += open ? 2 : -2;
        state.routePropagation.put(state.currentRoute, open ? "open" : "strict");
        String route = state.currentRoute;
        boolean entity = "entity".equals(state.routeRecords.get(route));
        if ((route.equals("A") || route.equals("C")) && entity && !open && state.desire >= 1) {
            gameOver(state, route.equals("A") ? "松岗残歌" : "冰歌消融", "传话灵蛙");
            return;
        }
        if (route.equals("B") && entity && state.inventory.contains("外来商人密信")) {
            gameOver(state, "利染灵纹", "荷叶蛙");
            return;
        }
        state.sceneId = "chat";
        state.sceneTitle = "守卷灵蛙的最后一问";
        state.speaker = route.equals("A") ? "护林蛙" : route.equals("B") ? "荷叶蛙" : "天池映雪蛙";
        state.choices = choices(
            choice(route + "-chat-1", "询问先民留下的故事", "解锁一则风物札记"),
            choice(route + "-chat-2", "询问守护古卷的岁月", "解锁一则守藏札记"),
            choice(route + "-chat-3", "提出关于流传的疑问", "听到灵蛙的辩证回答"),
            choice(route + "-chat-4", "不再多谈，直接返程", "回到萨满古洞")
        );
    }

    private void gameOver(StoryState state, String title, String speaker) {
        state.gameOver = true;
        state.sceneId = "game-over";
        state.sceneTitle = "中途结局 · " + title;
        state.speaker = speaker;
        state.choices = Collections.emptyList();
    }

    private void finishRoute(StoryState state, String choiceId) {
        if (!Set.of(state.currentRoute + "-chat-1", state.currentRoute + "-chat-2", state.currentRoute + "-chat-3", state.currentRoute + "-chat-4").contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的对话选择");
        }
        if (choiceId.endsWith("chat-1")) state.notes.add(state.currentRoute + "路线 · 先民故事");
        if (choiceId.endsWith("chat-2")) state.notes.add(state.currentRoute + "路线 · 守藏岁月");
        state.completedRoutes.add(state.currentRoute);
        state.sceneId = "hub";
        state.sceneTitle = "萨满古洞 · 路线回望";
        state.speaker = "传话灵蛙";
        if (!state.visionResolved && state.completedRoutes.size() == 1) {
            state.sceneId = "vision";
            state.sceneTitle = "支线 H · 白山主灵谕幻境";
            state.speaker = "白山主";
            state.choices = choices(
                choice("H-1", "请愿：改写灵纹，使古卷不可损毁且可离开故土", "代价：记忆损耗"),
                choice("H-2", "请愿：只修复残破古卷，不改动原生规则", "代价：永别林蛙谷"),
                choice("H-3", "拒绝请愿，依靠凡人的抄写与守护", "不获得隐藏终局")
            );
        } else if (state.completedRoutes.size() == ROUTES.size()) {
            showFinale(state);
        } else {
            showHub(state);
        }
    }

    private void chooseVision(StoryState state, String choiceId) {
        if (!Set.of("H-1", "H-2", "H-3").contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的幻境选择");
        }
        state.visionResolved = true;
        if (choiceId.equals("H-1")) {
            state.hiddenWish = "H1";
            addItem(state, "记忆损耗");
        } else if (choiceId.equals("H-2")) {
            state.hiddenWish = "H2";
            addItem(state, "永别林蛙谷");
        }
        showHub(state);
    }

    private void showHub(StoryState state) {
        state.sceneId = "hub";
        state.sceneTitle = "萨满古洞 · 选择下一步";
        state.speaker = "传话灵蛙";
        List<Map<String, Object>> next = new ArrayList<>();
        for (String route : ROUTES) {
            if (!state.completedRoutes.contains(route)) next.add(choice(route, ROUTE_NAMES.get(route), "进入路线"));
        }
        if (!state.completedRoutes.isEmpty() && state.completedRoutes.size() < ROUTES.size()) {
            next.add(choice("leave-early", "暂离林蛙谷", "触发特殊结局：歧路迷思 · 待续归谷"));
        }
        state.choices = next;
    }

    private void chooseHub(StoryState state, String choiceId) {
        if (choiceId.equals("leave-early")) {
            state.sceneId = "early-exit";
            finishStory(state, choiceId);
            return;
        }
        if (!ROUTES.contains(choiceId) || state.completedRoutes.contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择尚未完成的路线");
        }
        chooseRoute(state, choiceId);
    }

    private void showFinale(StoryState state) {
        state.sceneId = "finale";
        state.sceneTitle = "终局 · 古卷的命运";
        state.speaker = "传话灵蛙";
        List<Map<String, Object>> finalChoices = new ArrayList<>(choices(
            choice("final-1", "山林归藏", "实体归还故土，副本留在古洞"),
            choice("final-2", "人间活化", "实体归还故土，副本带往人间传播"),
            choice("final-3", "私藏束之", "实体与副本全部带出古洞")
        ));
        if (state.hiddenWish != null) finalChoices.add(choice("final-4", state.hiddenWish.equals("H1") ? "神改灵纹" : "神山修复", "使用白山主的隐藏契约"));
        state.choices = finalChoices;
    }

    private void finishStory(StoryState state, String choiceId) {
        if (choiceId.equals("early-exit")) {
            setEnding(state, "11", "歧路迷思 · 待续归谷", "你平安回到古洞，却尚未做好决定。灵族不会替你强行定夺，三份古卷仍安静守在故土。", "特殊结局");
            return;
        }
        if (!Set.of("final-1", "final-2", "final-3", "final-4").contains(choiceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的终局选择");
        }
        if (choiceId.equals("final-4")) {
            if (state.hiddenWish == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "隐藏终局尚未解锁");
            if (state.hiddenWish.equals("H1")) setEnding(state, "9", "梦碎忘歌", "神力改写了三份灵纹，古卷不再损毁；代价是你的记忆一点点剥离，你带着珍宝走出山谷，却再也记不起乌勒本。", "幻境变体结局");
            else setEnding(state, "10", "谷外人望山", "神山修复了所有古卷，你却永远失去再次踏入林蛙谷的资格。", "幻境变体结局");
            return;
        }
        if (choiceId.equals("final-1")) {
            if (all("copy", state.routeRecords) && all("strict", state.routePropagation)
                && state.reverence >= 4 && state.openness <= -2 && !state.inventory.contains("外来商人密信")) {
                setEnding(state, "1", "灵歌永续 · 守正传承", "三份实体古卷被灵族送回原生圣地，完整抄录副本留在古洞供后世阅览。乌勒本守住了根，也没有被锁进一人的私囊。", "常规完美结局");
            } else {
                setFallback(state);
            }
            return;
        }
        if (choiceId.equals("final-2")) {
            if (all("copy", state.routeRecords) && all("open", state.routePropagation) && state.reverence >= 5 && state.openness >= 5 && state.inventory.contains("传承人嘱托竹简")) {
                setEnding(state, "3", "守魂传歌", "你守住古卷本体，也守住通俗改编的内核；面向大众传播故事，同时为研究者留下完整原文。", "隐藏至高结局");
            } else if (all("copy", state.routeRecords) && all("open", state.routePropagation) && state.reverence >= 4 && state.openness >= 2 && state.openness <= 4) {
                setEnding(state, "2", "纸韵新鸣 · 现世活化", "实体古卷留在故土，完整副本走向 H5、角色故事卡与小游戏，古老说部借新时代媒介获得新生。", "项目标准目标结局");
            } else if (all("copy", state.routeRecords) && state.reverence >= 4 && countValue("open", state.routePropagation) == 2) {
                setEnding(state, "4", "半晦半明 · 割裂传歌", "部分乌勒本走向大众，另一部分仍被锁在古洞。传播开始了，却没有完整抵达人间。", "中性结局");
            } else if (hasValue("entity", state.routeRecords) && state.openness >= 1) {
                setEnding(state, "5", "残卷外传 · 伤痕传播", "副本得以传播，但至少一卷实体古卷因离开故土留下不可逆伤痕。", "负面中性结局");
            } else {
                setFallback(state);
            }
            return;
        }
        if (choiceId.equals("final-3")) {
            if (state.inventory.contains("外来商人密信") && state.desire >= 2) {
                setEnding(state, "8", "利欲焚卷", "商人的密信唤起贪欲，灵纹在愤怒的蛙鸣中焚毁，古洞的守护力量随之消散。", "隐藏坏结局");
            } else if (hasValue("entity", state.routeRecords) && state.desire < 2) {
                setEnding(state, "7", "灵纹零落 · 古卷散佚", "实体古卷脱离故土后持续消融，而你又拒绝向外传播，留下了双重损失。", "普通坏结局");
            } else if (all("copy", state.routeRecords) && all("strict", state.routePropagation) && state.desire < 2) {
                setEnding(state, "6", "秘卷孤守 · 锁藏沉寂", "所有副本被你带走，古洞不留备份。你守住了原文，却让乌勒本失去通往人间的钥匙。", "中性偏负面结局");
            } else {
                setFallback(state);
            }
        }
    }

    private void setFallback(StoryState state) {
        setEnding(state, "11", "歧路迷思 · 待续归谷", "你平安归来，却没有满足任何正式结局的完整条件。古卷保持原状，答案留待下一次归谷。", "特殊结局");
    }

    private void setEnding(StoryState state, String id, String title, String text, String label) {
        state.finished = true;
        state.sceneId = "ending";
        state.sceneTitle = "结局 " + id + " · " + title;
        state.speaker = "传话灵蛙";
        state.ending = Map.of("id", id, "title", title, "text", text, "label", label);
        state.choices = Collections.emptyList();
    }

    private boolean hasOrderEvent(StoryState state) {
        if (state.routeOrder.size() < 2) return false;
        String previous = state.routeOrder.get(state.routeOrder.size() - 2);
        String current = state.currentRoute;
        return previous.equals("C") && current.equals("A") || previous.equals("A") && current.equals("B") || previous.equals("B") && current.equals("C");
    }

    private List<Map<String, Object>> orderChoices(StoryState state) {
        String previous = state.routeOrder.get(state.routeOrder.size() - 2);
        if (previous.equals("C") && state.currentRoute.equals("A")) return choices(choice("A-S-A", "寻找护林蛙调和灵气", "请求灵族出手"), choice("A-S-B", "不予理会，直接找老萨满", "继续前行"), choice("A-S-C", "用破碎神牌尝试仪式", "自行平息寒风"));
        if (previous.equals("A") && state.currentRoute.equals("B")) return choices(choice("B-S-A", "认真倾听并收下补充告诫", "解锁《水泽传训》"), choice("B-S-B", "质疑残影并拒绝", "不获得札记"), choice("B-S-C", "直接离开", "继续前行"));
        return choices(choice("C-S-A", "当场戳穿商人并驱逐", "拒绝尾随"), choice("C-S-B", "假意未发现，之后周旋", "先处理古卷"), choice("C-S-C", "制造风雪障碍困住商人", "争取时间"), choice("C-S-D", "试探许诺，假意谈交易", "私欲倾向值 +1"));
    }

    private static String noteFor(String route, boolean brief) {
        if (route.equals("A")) return brief ? "窝集猎户铜牌（简版）" : "窝集猎户铜牌";
        if (route.equals("B")) return brief ? "水泽祠祷文（简版）" : "水泽祠祷文";
        return brief ? "冰祭祝辞（简版）" : "冰祭祝辞";
    }

    private static boolean validEncounterOneChoice(String route, String choiceId) {
        if (route.equals("A")) return Set.of("A-a-1", "A-a-2", "A-a-3", "A-a-4").contains(choiceId);
        if (route.equals("B")) return Set.of("B-a-1", "B-a-2", "B-a-3").contains(choiceId);
        return Set.of("C-a-1", "C-a-2", "C-a-3").contains(choiceId);
    }

    private static boolean validEncounterTwoChoice(String route, String choiceId) {
        if (route.equals("A")) return Set.of("A-b-1", "A-b-2", "A-b-3").contains(choiceId);
        if (route.equals("B")) return Set.of("B-b-1", "B-b-2").contains(choiceId);
        return Set.of("C-b-1", "C-b-2").contains(choiceId);
    }

    private static List<Map<String, Object>> choices(Map<String, Object>... values) {
        return new ArrayList<>(List.of(values));
    }

    private static Map<String, Object> choice(String id, String label, String hint) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("label", label);
        value.put("hint", hint);
        return value;
    }

    private static void addItem(StoryState state, String item) {
        state.inventory.add(item);
    }

    private static boolean all(String value, Map<String, String> source) {
        return source.size() == ROUTES.size() && source.values().stream().allMatch(value::equals);
    }

    private static boolean hasValue(String value, Map<String, String> source) {
        return source.values().stream().anyMatch(value::equals);
    }

    private static long countValue(String value, Map<String, String> source) {
        return source.values().stream().filter(value::equals).count();
    }

    private static String userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user.id();
    }

    // 包可见 + 无 final 字段：Jackson 按字段序列化/反序列化，实例从无参构造创建。
    // 可见性由注解声明，任何 ObjectMapper（含 Spring 全局实例）都能正确读写。
    @JsonAutoDetect(creatorVisibility = JsonAutoDetect.Visibility.ANY,
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static final class StoryState {
        private String sceneId = "intro";
        private String sceneTitle = "开场 · 萨满古洞";
        private String speaker = "传话灵蛙";
        private List<Map<String, Object>> choices = new ArrayList<>();
        private List<String> completedRoutes = new ArrayList<>();
        private List<String> routeOrder = new ArrayList<>();
        private Map<String, String> routeRecords = new LinkedHashMap<>();
        private Map<String, String> routePropagation = new LinkedHashMap<>();
        private List<String> inventory = new ArrayList<>();
        private List<String> notes = new ArrayList<>();
        private int reverence;
        private int openness;
        private int desire;
        private String currentRoute;
        private String hiddenWish;
        private boolean soulResolved;
        private boolean visionResolved;
        private boolean gameOver;
        private boolean finished;
        private Map<String, Object> ending;

        StoryState() {
            choices = choices(choice("A", ROUTE_NAMES.get("A"), "追寻《山林规约卷》"), choice("B", ROUTE_NAMES.get("B"), "追寻《水泽灵物卷》"), choice("C", ROUTE_NAMES.get("C"), "追寻《冰雪传说卷》"));
        }

        private void reset() {
            sceneId = "intro";
            sceneTitle = "开场 · 萨满古洞";
            speaker = "传话灵蛙";
            choices = choices(choice("A", ROUTE_NAMES.get("A"), "追寻《山林规约卷》"), choice("B", ROUTE_NAMES.get("B"), "追寻《水泽灵物卷》"), choice("C", ROUTE_NAMES.get("C"), "追寻《冰雪传说卷》"));
            completedRoutes.clear();
            routeOrder.clear();
            routeRecords.clear();
            routePropagation.clear();
            inventory.clear();
            notes.clear();
            reverence = 0;
            openness = 0;
            desire = 0;
            currentRoute = null;
            hiddenWish = null;
            soulResolved = false;
            visionResolved = false;
            gameOver = false;
            finished = false;
            ending = null;
        }

        /** 只下发客户端渲染需要的字段；数值、路线记录等判定依据留在服务端状态里。 */
        private Map<String, Object> toResponse(StoryContentCatalog content) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("gameId", GAME_ID);
            response.put("completed", completedRoutes.size());
            response.put("total", ROUTES.size());
            response.put("finished", finished);
            response.put("gameOver", gameOver);
            response.put("version", 1);
            response.put("updatedAt", Instant.now());
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("sceneId", sceneId);
            state.put("sceneTitle", sceneTitle);
            state.put("sceneParagraphs", content.paragraphs(contentKey()));
            state.put("speaker", speaker);
            state.put("choices", choices);
            state.put("completedRoutes", completedRoutes);
            state.put("inventory", inventory);
            state.put("notes", notes);
            state.put("ending", ending);
            response.put("state", state);
            return response;
        }

        private String contentKey() {
            return switch (sceneId) {
                case "intro" -> "intro";
                case "soul" -> "soul";
                case "order" -> routeOrder.size() < 2 ? "order-default" : "order-" + routeOrder.get(routeOrder.size() - 2) + "-" + currentRoute;
                case "encounter-1" -> "encounter-1-" + currentRoute;
                case "encounter-2" -> "encounter-2-" + currentRoute;
                case "guardian" -> "guardian-" + currentRoute;
                case "propagation" -> "propagation";
                case "chat" -> "chat-" + currentRoute;
                case "vision" -> "vision";
                case "hub" -> completedRoutes.isEmpty() ? "hub-empty" : "hub-progress";
                case "finale" -> "finale";
                case "game-over" -> "game-over-" + currentRoute;
                case "ending" -> "ending-" + (ending == null ? "11" : ending.get("id"));
                default -> throw new IllegalStateException("未知故事场景：" + sceneId);
            };
        }
    }
}
