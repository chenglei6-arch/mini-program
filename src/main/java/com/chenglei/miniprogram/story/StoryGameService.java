package com.chenglei.miniprogram.story;

import com.chenglei.miniprogram.auth.DevSessionService;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 《哈什玛灵纹之书》的服务端状态机。
 *
 * 文字内容和选择规则由服务端决定，客户端只提交 choiceId，避免把结局条件
 * 和数值判定暴露成可被客户端直接篡改的逻辑。
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

    private final Map<String, StoryState> states = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();

    public Map<String, Object> progress(Authentication authentication) {
        StoryState state = states.computeIfAbsent(userId(authentication), ignored -> new StoryState());
        return state.toResponse();
    }

    public synchronized Map<String, Object> reset(Authentication authentication) {
        String userId = userId(authentication);
        StoryState state = new StoryState();
        states.put(userId, state);
        idempotency.keySet().removeIf(key -> key.startsWith(userId + ":"));
        return state.toResponse();
    }

    public synchronized Map<String, Object> choose(Authentication authentication, String idempotencyKey, String choiceId) {
        String userId = userId(authentication);
        if (choiceId == null || choiceId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少故事选择");
        }
        StoryState state = states.computeIfAbsent(userId, ignored -> new StoryState());
        String operationKey = userId + ":" + (idempotencyKey == null || idempotencyKey.isBlank()
            ? UUID.randomUUID() : idempotencyKey);
        String previous = idempotency.get(operationKey);
        if (previous != null) {
            if (!previous.equals(choiceId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "幂等键已经用于其他故事选择");
            }
            return state.toResponse();
        }

        applyChoice(state, choiceId);
        idempotency.put(operationKey, choiceId);
        return state.toResponse();
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
        String route = state.currentRoute;
        if (!state.soulResolved) {
            state.sceneId = "soul";
            state.sceneTitle = "支线 S · 迷途寻访者亡魂";
            state.sceneText = "林间、河滩或雪原的空气骤然变冷。一个衣衫破败的残魂从雾气中浮现，他是数百年前失败的萨满学徒，仍被困在林蛙谷。";
            state.speaker = "旁白";
            state.choices = choices(
                choice("S-1", "倾听遗憾，举行简易超度仪式", "以悲悯回应亡魂"),
                choice("S-2", "无视亡魂，继续赶路", "不改变数值与背包"),
                choice("S-3", "威逼残魂，夺取遗物", "获得手记，但会增加私欲")
            );
            return;
        }
        state.temporaryStates.add(route.equals("A") ? "身上沾染松脂" : route.equals("B") ? "衣袖沾染湿地水汽" : "手指冻得僵硬");
        state.songs.add(route.equals("A") ? "山林古训一" : route.equals("B") ? "水泽安澜" : "日吉纳悲歌");
        if (hasOrderEvent(state)) {
            state.sceneId = "order";
            state.sceneTitle = "行进顺序留下的回声";
            state.sceneText = orderText(state);
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
                state.soulRelief += 2;
                addItem(state, "旧寻访手记");
                addItem(state, "破碎萨满神牌");
                state.notes.add("迷途寻访者的遗憾");
                state.sceneText = "残魂向你吐露失败往事，将旧寻访手记与破碎萨满神牌交给你。它化为点点微光，终于离开了林蛙谷。";
            }
            case "S-2" -> state.sceneText = "你径直离开。残魂在身后低声叹息，慢慢隐入雾气。";
            case "S-3" -> {
                state.desire += 2;
                state.soulRelief -= 2;
                addItem(state, "旧寻访手记");
                state.sceneText = "你以言语威慑夺下旧寻访手记，残魂在痛苦尖叫中溃散。";
            }
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
        state.sceneText = route.equals("A")
            ? "腐朽的桦树皮帐篷骨架歪斜立在松林间，地面散落青铜狩猎牌、兽骨和萨满祭祀陶片。"
            : route.equals("B")
                ? "湿地河滩边矗立着一座水神小石祠，风化石壁上依稀可见蛙戏莲纹样。"
                : "厚雪下半掩着一座祭冰石台，石缝里还留有干枯的祭祀桦树枝。";
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
        state.sceneText = route.equals("A")
            ? "粗壮红松树干下，一位白发老萨满虚弱地倚坐着。他是世间少数还能完整唱诵山林篇乌勒本的传承人。"
            : route.equals("B")
                ? "一位外来商人从芦苇荡中走出，递来火漆封缄的密信，目光始终盯着水下石室。"
                : "冰雪下露出一册残破手抄古本，纸页冻得发脆，留存着日吉纳格格传说的残片。";
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
        state.sceneText = route.equals("A")
            ? "界碑石后的石龛中，《山林规约卷》被松绿色地气包裹。护林蛙提醒你，古卷离开故土便会逐层衰败。"
            : route.equals("B")
                ? "荷叶蛙静栖在碧绿荷叶上，水下石室安放着《水泽灵物卷》。它警惕地扫过你的行囊。"
                : "天池映雪蛙立于冰原，裂隙深处安放着《冰雪传说卷》。酷寒是这份悲壮史诗的根基。";
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
        state.sceneText = "实体与副本的命运已经分开。现在请决定，你是否允许后人用更易理解的方式接近乌勒本。";
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
            state.gameOver = true;
            state.sceneId = "game-over";
            state.sceneTitle = route.equals("A") ? "中途结局 · 松岗残歌" : "中途结局 · 冰歌消融";
            state.sceneText = route.equals("A")
                ? "你拿走实体古卷，又立誓绝不传播。山雾吞噬了所有道路，古卷在行囊中慢慢失去光彩。你困死在红松林海之中。"
                : "你把实体古卷带离冰渊，又拒绝向外讲述。暴风雪封死下山道路，冰雪灵纹不断消融。你困在天池雪原。";
            state.speaker = "传话灵蛙";
            state.choices = Collections.emptyList();
            return;
        }
        if (route.equals("B") && entity && state.inventory.contains("外来商人密信")) {
            state.gameOver = true;
            state.sceneId = "game-over";
            state.sceneTitle = "中途结局 · 利染灵纹";
            state.sceneText = "你想起商人的酬金，决定把实体古卷交给市场。荷塘翻涌，芦苇疯长，归途被彻底遮蔽。古卷在袋中快速干枯损毁。";
            state.speaker = "荷叶蛙";
            state.choices = Collections.emptyList();
            return;
        }
        state.sceneId = "chat";
        state.sceneTitle = "守卷灵蛙的最后一问";
        state.sceneText = "古卷暂时安定下来。你还可以留下一段谈话，或直接踏上返回萨满古洞的路。";
        state.speaker = route.equals("A") ? "护林蛙" : route.equals("B") ? "荷叶蛙" : "天池映雪蛙";
        state.choices = choices(
            choice(route + "-chat-1", "询问先民留下的故事", "解锁一则风物札记"),
            choice(route + "-chat-2", "询问守护古卷的岁月", "解锁一则守藏札记"),
            choice(route + "-chat-3", "提出关于流传的疑问", "听到灵蛙的辩证回答"),
            choice(route + "-chat-4", "不再多谈，直接返程", "回到萨满古洞")
        );
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
        state.sceneText = "你带着新的札记与古卷命运回到古洞。篝火映亮石台，尚未探索的地域仍在等待。";
        state.speaker = "传话灵蛙";
        if (!state.visionResolved && state.completedRoutes.size() == 1) {
            state.sceneId = "vision";
            state.sceneTitle = "支线 H · 白山主灵谕幻境";
            state.sceneText = "返回途中天地骤然变色，你来到悬浮云海。白山主的宏大虚影降临，询问你是否愿意以神山捷径改写古卷命运。";
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
        state.sceneText = state.completedRoutes.size() == 0
            ? "传话灵蛙的微光重新稳定下来。你的第一步将迈向何方？"
            : "篝火等待着下一卷古卷的回声。你也可以暂时离开，把一路所见带回人间思索。";
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
        state.sceneText = "三卷全部探索完毕。你站在古洞篝火下，实体古卷或抄录副本悬浮在石台之前。现在，古卷的命运由你定夺。";
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
        state.sceneText = text;
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

    private String orderText(StoryState state) {
        String previous = state.routeOrder.get(state.routeOrder.size() - 2);
        if (previous.equals("C") && state.currentRoute.equals("A")) return "不属于红松林的冰雪残风灌入山口，部分山林灵纹被寒气侵蚀。";
        if (previous.equals("A") && state.currentRoute.equals("B")) return "湿地薄雾中浮现老萨满残影，他提醒你水泽歌谣更容易被世俗曲解。";
        return "风雪中，你发现商人尾随来到冰原，仍在觊觎冰雪古卷。";
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
        if (authentication == null || !(authentication.getPrincipal() instanceof DevSessionService.User user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user.id();
    }

    private static final class StoryState {
        private String sceneId = "intro";
        private String sceneTitle = "开场 · 萨满古洞";
        private String sceneText = "穿过缠绕盘结的老藤山隘，你来到萨满古洞。传话灵蛙告诉你，三份灵纹古卷必须留在故土，而副本可以走向人间。现在，你的第一步将迈向何方？";
        private String speaker = "传话灵蛙";
        private List<Map<String, Object>> choices = new ArrayList<>();
        private final List<String> completedRoutes = new ArrayList<>();
        private final List<String> routeOrder = new ArrayList<>();
        private final Map<String, String> routeRecords = new LinkedHashMap<>();
        private final Map<String, String> routePropagation = new LinkedHashMap<>();
        private final List<String> inventory = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private final List<String> songs = new ArrayList<>();
        private final List<String> temporaryStates = new ArrayList<>();
        private int reverence;
        private int openness;
        private int desire;
        private int soulRelief;
        private String currentRoute;
        private String hiddenWish;
        private boolean soulResolved;
        private boolean visionResolved;
        private boolean gameOver;
        private boolean finished;
        private Map<String, Object> ending;

        private StoryState() {
            choices = choices(choice("A", ROUTE_NAMES.get("A"), "追寻《山林规约卷》"), choice("B", ROUTE_NAMES.get("B"), "追寻《水泽灵物卷》"), choice("C", ROUTE_NAMES.get("C"), "追寻《冰雪传说卷》"));
        }

        private void reset() {
            sceneId = "intro";
            sceneTitle = "开场 · 萨满古洞";
            sceneText = "穿过缠绕盘结的老藤山隘，你来到萨满古洞。传话灵蛙告诉你，三份灵纹古卷必须留在故土，而副本可以走向人间。现在，你的第一步将迈向何方？";
            speaker = "传话灵蛙";
            choices = choices(choice("A", ROUTE_NAMES.get("A"), "追寻《山林规约卷》"), choice("B", ROUTE_NAMES.get("B"), "追寻《水泽灵物卷》"), choice("C", ROUTE_NAMES.get("C"), "追寻《冰雪传说卷》"));
            completedRoutes.clear();
            routeOrder.clear();
            routeRecords.clear();
            routePropagation.clear();
            inventory.clear();
            notes.clear();
            songs.clear();
            temporaryStates.clear();
            reverence = 0;
            openness = 0;
            desire = 0;
            soulRelief = 0;
            currentRoute = null;
            hiddenWish = null;
            soulResolved = false;
            visionResolved = false;
            gameOver = false;
            finished = false;
            ending = null;
        }

        private Map<String, Object> toResponse() {
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
            state.put("sceneText", sceneText);
            state.put("speaker", speaker);
            state.put("choices", choices);
            state.put("currentRoute", currentRoute);
            state.put("completedRoutes", completedRoutes);
            state.put("routeOrder", routeOrder);
            state.put("routeRecords", routeRecords);
            state.put("routePropagation", routePropagation);
            state.put("inventory", inventory);
            state.put("notes", notes);
            state.put("songs", songs);
            state.put("temporaryStates", temporaryStates);
            state.put("scores", Map.of("reverence", reverence, "openness", openness, "desire", desire, "soulRelief", soulRelief));
            state.put("hiddenWish", hiddenWish);
            state.put("ending", ending);
            response.put("state", state);
            return response;
        }
    }
}
