package com.chenglei.miniprogram;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chenglei.miniprogram.integration.ContentCatalogMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "app.content.include-test-data=true",
    // 测试环境不连微信：必须显式声明开发登录 openid，否则登录接口会按未配置凭据直接报错。
    "app.auth.dev-openid=dev-local-user"
})
@AutoConfigureMockMvc
class MiniProgramBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContentCatalogMapper contentCatalogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearGuardianState() {
        jdbcTemplate.update("DELETE FROM guardian_event");
        jdbcTemplate.update("DELETE FROM guardian_round");
        jdbcTemplate.update("DELETE FROM guardian_collection");
        jdbcTemplate.update("DELETE FROM guardian_daily_state");
        jdbcTemplate.update("DELETE FROM game_progress");
        jdbcTemplate.update("DELETE FROM game_event");
        jdbcTemplate.update("DELETE FROM user_badge");
        jdbcTemplate.update("DELETE FROM mall_order");
        jdbcTemplate.update("UPDATE unlock_code SET status='unused', redeemed_by=NULL, redeemed_at=NULL");
    }

    @Test
    void unlockCodesAreSingleUseAndValidated() throws Exception {
        String token = loginToken("unlock-test-code");
        String redeemBody = "{\"code\":\"PAPER-FROG-2026-0002\"}";

        mockMvc.perform(post("/v1/unlocks/redeem")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(redeemBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characterId").value("hibernation"))
            .andExpect(jsonPath("$.data.character.name").value("冬眠蛙"))
            .andExpect(jsonPath("$.data.character.sections").isArray());

        // 同一二维码只能使用一次。
        mockMvc.perform(post("/v1/unlocks/redeem")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(redeemBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COMMON_409"));

        // 码池外的二维码无效。
        mockMvc.perform(post("/v1/unlocks/redeem")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"code\":\"NOT-A-REAL-CODE\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMON_404"));

        // 核销结果落在 unlock_code 上（redeemed_by/redeemed_at），不再另有审计表。
        org.junit.jupiter.api.Assertions.assertEquals("redeemed", jdbcTemplate.queryForObject(
            "SELECT status FROM unlock_code WHERE code = 'PAPER-FROG-2026-0002'", String.class));
    }

    @Test
    void contextLoadsAndPingEndpointResponds() throws Exception {
        mockMvc.perform(get("/v1/system/ping"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", not(blankOrNullString())))
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.status").value("up"));
    }

    @Test
    void protectedEndpointUsesUnifiedUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/v1/protected"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @Test
    void loginTokenCanAccessFrontendContractEndpoints() throws Exception {
        MvcResult login = mockMvc.perform(post("/v1/auth/wechat-login")
                .contentType("application/json")
                .content("{\"code\":\"local-test-code\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andReturn();

        String token = objectMapper
            .readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        mockMvc.perform(get("/v1/home/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.brand").value("蛙声说部·哈什蚂传奇"))
            .andExpect(jsonPath("$.data.games").isArray())
            .andExpect(jsonPath("$.data.frogs[0].assetUrl").value("https://assets.test.local/assets/frogs/forest.png"));


        mockMvc.perform(get("/v1/content/frogs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(10))
            .andExpect(jsonPath("$.data.items[8].pattern").value("空气纹"))
            .andExpect(jsonPath("$.data.items[9].name").value("林蛙测试数据-001"));

        org.junit.jupiter.api.Assertions.assertEquals(1, contentCatalogMapper.countTestFrogs());
        org.junit.jupiter.api.Assertions.assertEquals(9, contentCatalogMapper.selectFrogs(false).size());
        org.junit.jupiter.api.Assertions.assertEquals(10, contentCatalogMapper.selectFrogs(true).size());

        mockMvc.perform(get("/v1/content/frogs/hibernation").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("冬眠蛙"))
            .andExpect(jsonPath("$.data.assetUrl").value("https://assets.test.local/assets/frogs/hibernation.png"))
            .andExpect(jsonPath("$.data.sections[0].heading").value("一、整体构图"))
            .andExpect(jsonPath("$.data.sections[0].paragraphs[0]").isString());

        mockMvc.perform(get("/assets/frogs/forest.png"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));

        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            // 开发登录（未配置 WECHAT_APPID/SECRET）映射到固定 app_user，H2 首个登录用户 id 为 1。
            .andExpect(jsonPath("$.data.user.id").value("1"))
            .andExpect(jsonPath("$.data.stats.frogs").value(0));

        mockMvc.perform(get("/v1/welfare/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fundAmount").value("12,480.00"));

        mockMvc.perform(post("/v1/unlocks/redeem")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"code\":\"PAPER-FROG-2026-0001\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.characterName").value("护林蛙"))
            .andExpect(jsonPath("$.data.character.blessing").isNotEmpty());

        mockMvc.perform(get("/v1/rankings?type=total").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("total"))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.myRank").doesNotExist());

        // 未登记的游戏直接 404，不再有通用进度兜底。
        mockMvc.perform(get("/v1/games/paper-cutting/progress").header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    @Test
    void devLoginReusesSameAccountAndPersistsProfile() throws Exception {
        String firstToken = loginToken("dev-login-001");
        String secondToken = loginToken("dev-login-002");

        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + firstToken))
            .andExpect(status().isOk());
        MvcResult secondProfile = mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + secondToken))
            .andExpect(status().isOk())
            .andReturn();
        String sharedUserId = objectMapper.readTree(secondProfile.getResponse().getContentAsString())
            .path("data").path("user").path("id").asText();

        mockMvc.perform(patch("/v1/me/profile")
                .header("Authorization", "Bearer " + secondToken)
                .contentType("application/json")
                .content("{\"nickname\":\"持久化用户\"}"))
            .andExpect(status().isOk());

        // 同一开发账号再次登录后，昵称修改依然可读（落在 app_user 表而不是内存）。
        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + firstToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.id").value(sharedUserId))
            .andExpect(jsonPath("$.data.user.nickname").value("持久化用户"));

        // 未知 token 不能通过鉴权。
        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @Test
    void profilePatchValidatesAndReturnsUpdatedUser() throws Exception {
        MvcResult login = mockMvc.perform(post("/v1/auth/wechat-login")
                .contentType("application/json")
                .content("{\"code\":\"profile-test-code\"}"))
            .andReturn();
        String token = objectMapper
            .readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        mockMvc.perform(patch("/v1/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"nickname\":\"联调用户\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname").value("联调用户"));
    }

    @Test
    void storyGameExposesStateMachineAndRejectsInvalidChoices() throws Exception {
        String token = loginToken("story-test-code");
        resetStory(token);

        mockMvc.perform(get("/v1/games/story/progress").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.gameId").value("story"))
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.state.sceneId").value("intro"))
            .andExpect(jsonPath("$.data.state.sceneParagraphs.length()").value(15))
            .andExpect(jsonPath("$.data.state.sceneParagraphs[0]").value("穿过缠绕盘结的老藤山隘，厚重的藤蔓如同幕布向两侧缓缓分开，露出幽深的萨满古洞入口。洞口岩壁历经数百年风霜侵蚀，层层叠叠拓印着萨满祭祀剪纸：哈什玛蛙纹、万字松枝护佑纹、蛙戏莲水纹、冰雪云纹。不少纹样雨水冲刷斑驳残缺，部分只剩下浅浅凹痕。"))
            .andExpect(jsonPath("$.data.state.choices[0].id").value("A"));

        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "story-choice-001")
                .contentType("application/json")
                .content("{\"type\":\"story_choice\",\"payload\":{\"choiceId\":\"A\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state.sceneId").value("soul"))
            // 路线等判定依据留在服务端状态里，不下发到响应。
            .andExpect(jsonPath("$.data.state.currentRoute").doesNotExist())
            .andExpect(jsonPath("$.data.state.sceneTitle").value("支线 S · 迷途寻访者亡魂"))
            .andExpect(jsonPath("$.data.state.choices[0].id").value("S-1"));

        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "story-choice-002")
                .contentType("application/json")
                .content("{\"type\":\"story_choice\",\"payload\":{\"choiceId\":\"final-1\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void storyGameSupportsIdempotencyAndReset() throws Exception {
        String token = loginToken("story-idempotency-code");
        resetStory(token);
        String request = "{\"type\":\"story_choice\",\"payload\":{\"choiceId\":\"A\"}}";

        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "story-repeat-001")
                .contentType("application/json")
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state.sceneId").value("soul"));

        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "story-repeat-001")
                .contentType("application/json")
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state.sceneId").value("soul"));

        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"type\":\"reset\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state.sceneId").value("intro"))
            .andExpect(jsonPath("$.data.completed").value(0));
    }

    @Test
    void guardianGameValidatesAnswersTracksCollectionAndGrantsShareBonus() throws Exception {
        String token = loginToken("guardian-test-code");

        mockMvc.perform(get("/v1/games/guardian/progress").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completed").value(0))
            .andExpect(jsonPath("$.data.total").value(9))
            .andExpect(jsonPath("$.data.state.remainingDraws").value(3))
            .andExpect(jsonPath("$.data.state.activeChallenge").doesNotExist());

        JsonNode firstChallenge = drawGuardian(token, "guardian-draw-0001");
        String firstRoundId = firstChallenge.path("roundId").asText();
        String firstFrogId = firstChallenge.path("frogId").asText();
        String firstCorrectPattern = String.valueOf(contentCatalogMapper.selectFrog(firstFrogId, false).get("pattern"));
        String wrongPattern = null;
        for (JsonNode option : firstChallenge.path("options")) {
            if (!option.asText().equals(firstCorrectPattern)) {
                wrongPattern = option.asText();
                break;
            }
        }
        if (wrongPattern == null) throw new IllegalStateException("守护神题目未返回错误选项");

        mockMvc.perform(post("/v1/games/guardian/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "guardian-answer-0001")
                .contentType("application/json")
                .content(answerEvent(firstRoundId, wrongPattern)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.action.correct").value(false))
            .andExpect(jsonPath("$.data.progress.completed").value(0))
            .andExpect(jsonPath("$.data.progress.state.activeChallenge.roundId").value(firstRoundId));

        String correctAnswer = answerEvent(firstRoundId, firstCorrectPattern);
        mockMvc.perform(post("/v1/games/guardian/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "guardian-answer-0002")
                .contentType("application/json")
                .content(correctAnswer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.action.correct").value(true))
            .andExpect(jsonPath("$.data.action.guardianCard.id").value(firstFrogId))
            .andExpect(jsonPath("$.data.action.newlyUnlockedBadges[0].id").value("guardian-first"))
            .andExpect(jsonPath("$.data.progress.completed").value(1))
            .andExpect(jsonPath("$.data.progress.state.activeChallenge").doesNotExist());

        mockMvc.perform(post("/v1/games/guardian/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "guardian-answer-0002")
                .contentType("application/json")
                .content(correctAnswer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.duplicated").value(true))
            .andExpect(jsonPath("$.data.progress.completed").value(1));

        mockMvc.perform(post("/v1/games/guardian/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "guardian-share-0001")
                .contentType("application/json")
                .content("{\"type\":\"share\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.action.shareBonusGranted").value(1))
            .andExpect(jsonPath("$.data.progress.state.shareBonusClaimed").value(true))
            .andExpect(jsonPath("$.data.progress.state.remainingDraws").value(3));

        answerGuardianCorrectly(token, "guardian-draw-0002", "guardian-answer-0003");
        answerGuardianCorrectly(token, "guardian-draw-0003", "guardian-answer-0004");

        mockMvc.perform(get("/v1/games/guardian/progress").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completed").value(3))
            .andExpect(jsonPath("$.data.state.collectedFrogIds.length()").value(3))
            .andExpect(jsonPath("$.data.state.badges[1].id").value("guardian-messenger"))
            .andExpect(jsonPath("$.data.state.badges[1].unlocked").value(true));

        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stats.frogs").value(3))
            .andExpect(jsonPath("$.data.badges[3].id").value("guardian-messenger"))
            .andExpect(jsonPath("$.data.badges[3].unlocked").value(true));
    }

    @Test
    void storyAndPuzzleProgressPersistToDatabase() throws Exception {
        String token = loginToken("persistence-test-code");
        resetStory(token);

        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "story-persist-001")
                .contentType("application/json")
                .content("{\"type\":\"story_choice\",\"payload\":{\"choiceId\":\"A\"}}"))
            .andExpect(status().isOk());

        // 剧情状态与幂等事件都写入 V1 预留的表，后端重启后进度不再丢失。
        org.junit.jupiter.api.Assertions.assertEquals(1, countGameProgress("story"));
        org.junit.jupiter.api.Assertions.assertEquals(1, countGameEvents("story", "story-persist-001"));

        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"type\":\"reset\"}"))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(0, countGameProgress("story"));
        org.junit.jupiter.api.Assertions.assertEquals(0, countGameEvents("story", "story-persist-001"));

        mockMvc.perform(post("/v1/games/frog-puzzle/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "puzzle-persist-001")
                .contentType("application/json")
                .content("{\"type\":\"frog_completed\",\"payload\":{\"frogId\":\"forest\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalPoints").value(10));

        org.junit.jupiter.api.Assertions.assertEquals(1, countGameProgress("frog-puzzle"));
        org.junit.jupiter.api.Assertions.assertEquals(1, countGameEvents("frog-puzzle", "puzzle-persist-001"));

        // 同一幂等键重放只算一次，积分不重复累计。
        mockMvc.perform(post("/v1/games/frog-puzzle/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "puzzle-persist-001")
                .contentType("application/json")
                .content("{\"type\":\"frog_completed\",\"payload\":{\"frogId\":\"forest\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.duplicated").value(true));

        mockMvc.perform(get("/v1/games/frog-puzzle/progress").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completedFrogs.length()").value(1))
            .andExpect(jsonPath("$.data.totalPoints").value(10));
    }

    @Test
    void badgesUnlockAcrossGamesAndPersist() throws Exception {
        String token = loginToken("badge-test-code");

        // 拼图完成 1 只蛙 → 剪纸新手（青铜）
        mockMvc.perform(post("/v1/games/frog-puzzle/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "badge-puzzle-001")
                .contentType("application/json")
                .content("{\"type\":\"frog_completed\",\"payload\":{\"frogId\":\"forest\"}}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.badges[0].id").value("paper-beginner"))
            .andExpect(jsonPath("$.data.badges[0].unlocked").value(true))
            .andExpect(jsonPath("$.data.stats.badges").value(1));

        // 剧情完整走完 1 条路线 → 故事聆听者（青铜）。状态机分支较多，
        // 测试里按服务端返回的选项逐场景选择，直到 completedRoutes >= 1。
        resetStory(token);
        int storyBadgesBefore = currentStatsBadges(token);
        for (int i = 0; i < 60; i++) {
            JsonNode state = storyState(token);
            if (state.path("completedRoutes").size() >= 1 || state.path("finished").asBoolean(false)
                || state.path("gameOver").asBoolean(false)) break;
            JsonNode choices = state.path("choices");
            if (!choices.isArray() || choices.isEmpty()) break;
            String choiceId = choices.get(0).path("id").asText();
            mockMvc.perform(post("/v1/games/story/events")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", "badge-story-walk-" + i)
                    .contentType("application/json")
                    .content("{\"type\":\"story_choice\",\"payload\":{\"choiceId\":\"" + choiceId + "\"}}"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.badges[1].id").value("story-listener"))
            .andExpect(jsonPath("$.data.badges[1].unlocked").value(true))
            .andExpect(jsonPath("$.data.stats.badges").value(storyBadgesBefore + 1));

        org.junit.jupiter.api.Assertions.assertEquals(storyBadgesBefore + 1, countUserBadges());
    }

    private JsonNode storyState(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/games/story/progress").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("state");
    }

    private int currentStatsBadges(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("stats").path("badges").asInt();
    }

    @Test
    void rankingsWelfareAndActivityServeRealData() throws Exception {
        String token = loginToken("rankings-test-code");

        mockMvc.perform(post("/v1/games/frog-puzzle/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "rankings-puzzle-001")
                .contentType("application/json")
                .content("{\"type\":\"frog_completed\",\"payload\":{\"frogId\":\"forest\"}}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/rankings?type=total").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].rank").value(1))
            .andExpect(jsonPath("$.data.items[0].score").value(1))
            .andExpect(jsonPath("$.data.items[0].nickname").isNotEmpty())
            .andExpect(jsonPath("$.data.myRank").value(1));

        mockMvc.perform(get("/v1/rankings?type=weekly").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.myRank").value(1));

        mockMvc.perform(get("/v1/welfare/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reports.length()").value(2))
            .andExpect(jsonPath("$.data.reports[0].title").isNotEmpty())
            .andExpect(jsonPath("$.data.reports[0].date").isNotEmpty());

        mockMvc.perform(get("/v1/home/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.activity[0]").value(org.hamcrest.Matchers.containsString("解锁了")));
    }

    @Test
    void mallSupportsBlindBoxOrderingWithIdempotency() throws Exception {
        String token = loginToken("mall-test-code");

        // 商品目录来自 mall_product 表，启动时空表会补入唯一的正式商品。
        mockMvc.perform(get("/v1/mall/products").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value("paper-cut-blindbox"))
            .andExpect(jsonPath("$.data.items[0].priceCents").value(2990));

        String orderBody = "{\"productId\":\"paper-cut-blindbox\",\"quantity\":2,"
            + "\"receiverName\":\"联调用户\",\"receiverPhone\":\"13800138000\","
            + "\"receiverAddress\":\"吉林省长春市净月区测试路 1 号\"}";

        MvcResult firstOrder = mockMvc.perform(post("/v1/mall/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "mall-order-001")
                .contentType("application/json")
                .content(orderBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.data.totalCents").value(5980))
            .andExpect(jsonPath("$.data.orderNo").isString())
            .andReturn();
        String orderNo = objectMapper.readTree(firstOrder.getResponse().getContentAsString())
            .path("data").path("orderNo").asText();

        // 同一幂等键重放返回同一订单，不重复建单。
        mockMvc.perform(post("/v1/mall/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "mall-order-001")
                .contentType("application/json")
                .content(orderBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value(orderNo));
        org.junit.jupiter.api.Assertions.assertEquals(1, countMallOrders());

        // 商品不存在返回 404；收货手机号不合法返回 400。
        mockMvc.perform(post("/v1/mall/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "mall-order-002")
                .contentType("application/json")
                .content("{\"productId\":\"no-such-product\",\"quantity\":1,"
                    + "\"receiverName\":\"联调用户\",\"receiverPhone\":\"13800138000\",\"receiverAddress\":\"地址\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMON_404"));

        mockMvc.perform(post("/v1/mall/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "mall-order-003")
                .contentType("application/json")
                .content("{\"productId\":\"paper-cut-blindbox\",\"quantity\":1,"
                    + "\"receiverName\":\"联调用户\",\"receiverPhone\":\"123\",\"receiverAddress\":\"地址\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON_400"));
        org.junit.jupiter.api.Assertions.assertEquals(1, countMallOrders());

        mockMvc.perform(get("/v1/mall/orders").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].orderNo").value(orderNo))
            .andExpect(jsonPath("$.data.items[0].productName").value("“林小蛙”潮玩盲盒"))
            .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty());
    }

    private int countMallOrders() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mall_order", Integer.class);
        return count == null ? 0 : count;
    }

    private int countUserBadges() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_badge", Integer.class);
        return count == null ? 0 : count;
    }

    private int countGameProgress(String gameId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_progress WHERE game_id = ?", Integer.class, gameId);
        return count == null ? 0 : count;
    }

    private int countGameEvents(String gameId, String eventKey) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM game_event WHERE game_id = ? AND event_key = ?", Integer.class, gameId, eventKey);
        return count == null ? 0 : count;
    }

    private String loginToken(String code) throws Exception {
        MvcResult login = mockMvc.perform(post("/v1/auth/wechat-login")
                .contentType("application/json")
                .content("{\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private void resetStory(String token) throws Exception {
        mockMvc.perform(post("/v1/games/story/events")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"type\":\"reset\"}"))
            .andExpect(status().isOk());
    }

    private JsonNode drawGuardian(String token, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/games/guardian/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content("{\"type\":\"draw\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.action.drawn").value(true))
            .andExpect(jsonPath("$.data.progress.state.remainingDraws").isNumber())
            .andExpect(jsonPath("$.data.progress.state.activeChallenge.options.length()").value(3))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("progress").path("state").path("activeChallenge");
    }

    private void answerGuardianCorrectly(String token, String drawKey, String answerKey) throws Exception {
        JsonNode challenge = drawGuardian(token, drawKey);
        String frogId = challenge.path("frogId").asText();
        String pattern = String.valueOf(contentCatalogMapper.selectFrog(frogId, false).get("pattern"));
        mockMvc.perform(post("/v1/games/guardian/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", answerKey)
                .contentType("application/json")
                .content(answerEvent(challenge.path("roundId").asText(), pattern)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.action.correct").value(true));
    }

    private String answerEvent(String roundId, String pattern) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
            "type", "answer",
            "payload", java.util.Map.of("roundId", roundId, "pattern", pattern)
        ));
    }
}
