package com.chenglei.miniprogram;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MiniProgramBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
            .andExpect(jsonPath("$.data.games").isArray());

        mockMvc.perform(get("/v1/me/profile").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.id").value("local-user"));

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
}
