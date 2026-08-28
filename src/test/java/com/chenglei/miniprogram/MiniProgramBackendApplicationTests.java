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

@SpringBootTest(properties = "app.content.include-test-data=true")
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
            .andExpect(jsonPath("$.data.brand").value("纸韵蛙鸣·哈什蚂传奇"))
            .andExpect(jsonPath("$.data.games").isArray())
            .andExpect(jsonPath("$.data.frogs[0].assetUrl").value("/assets/frogs/forest.png"));


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
            .andExpect(jsonPath("$.data.assetUrl").value("/assets/frogs/hibernation.png"))
            .andExpect(jsonPath("$.data.sourceUrl").value("/assets/sources/frogs/hibernation.docx"))
            .andExpect(jsonPath("$.data.sections[0].heading").value("一、整体构图"))
            .andExpect(jsonPath("$.data.sections[0].paragraphs[0]").isString());

        mockMvc.perform(get("/assets/frogs/forest.png"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));

        mockMvc.perform(get("/assets/sources/frogs/hibernation.docx"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.id").value("local-user"))
            .andExpect(jsonPath("$.data.stats.frogs").value(0));

        mockMvc.perform(get("/v1/welfare/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fundAmount").value("12,480.00"));

        mockMvc.perform(post("/v1/unlocks/redeem")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"code\":\"local-test-code\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.characterName").value("护林蛙"));

        mockMvc.perform(get("/v1/rankings?type=total").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.type").value("total"))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.myRank").doesNotExist());

        mockMvc.perform(post("/v1/games/paper-cutting/events")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "local-test-event-001")
                .contentType("application/json")
                .content("{\"type\":\"completed\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.progress.finished").value(true));
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
            .andExpect(jsonPath("$.data.nickname").value("联调用户"))
            .andExpect(jsonPath("$.data.isGuest").value(false));
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
            .andExpect(jsonPath("$.data.state.currentRoute").value("A"));

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
