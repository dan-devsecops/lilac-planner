package com.lilac.planner.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lilac.planner.persistence.PlannerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role gating of {@code GET/POST /api/v1/users} with native auth: only an ADMIN may list or
 * create users. The first registered user becomes ADMIN (mirrors {@link NativeAuthIT}); a
 * second registration yields a plain USER who must be rejected with 403, closing the user
 * enumeration / username pre-squatting hole these endpoints used to expose.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = {
        "planner.auth.provider=native",
        "planner.auth.native.jwt-secret=test-secret-test-secret-test-secret-1234",
        "planner.auth.native.access-ttl=PT15M",
        "planner.rate-limit.login-per-minute=10000",
        "planner.rate-limit.register-per-minute=10000",
})
@DisplayName("User API - admin-only list/create (native auth)")
class UserControllerAdminIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlannerStore store;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void registerAdminAndUser() throws Exception {
        store.resetAllData();
        register("alice", "alice@x.com", "password1");   // first user → ADMIN
        register("bob", "bob@x.com", "password1");       // second user → USER
        adminToken = login("alice", "password1");
        userToken = login("bob", "password1");
    }

    private void register(String username, String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("username", username);
                            put("email", email);
                            put("displayName", username);
                            put("password", password);
                        }})))
                .andExpect(status().isCreated());
    }

    private String login(String login, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    // --- non-admin and anonymous are rejected ---

    @Test
    @DisplayName("GET /api/v1/users: anonymous → 401, plain USER → 403")
    void listUsers_requiresAdmin() throws Exception {
        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/users: anonymous → 401, plain USER → 403 (no username pre-squatting)")
    void createUser_requiresAdmin() throws Exception {
        String body = """
                {"username":"charlie","displayName":"Charlie Brown"}
                """;

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        // the username was NOT squatted - bob's 403 left no user row behind
        org.assertj.core.api.Assertions.assertThat(store.findUserByUsername("charlie")).isEmpty();
    }

    // --- admin happy paths ---

    @Test
    @DisplayName("GET /api/v1/users as ADMIN returns the user list")
    void listUsers_asAdmin_returnsUsers() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.username=='alice')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.username=='bob')]").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/users as ADMIN creates a new user and returns it")
    void createUser_asAdmin_returnsCreatedUser() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"charlie","displayName":"Charlie Brown"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("charlie"))
                .andExpect(jsonPath("$.displayName").value("Charlie Brown"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/users as ADMIN falls back to username as displayName when displayName is blank")
    void createUser_asAdmin_blankDisplayName_fallsBackToUsername() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dana","displayName":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("dana"));
    }
}
