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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the actuator endpoint access rules are correctly applied in native-auth mode:
 * <ul>
 *   <li>{@code /actuator/health} and {@code /actuator/info} - public (required by the CD
 *       healthcheck and load-balancer probes).</li>
 *   <li>{@code /actuator/prometheus} - gated by its own Basic Auth credential (see
 *       {@code SecurityConfig#metricsFilterChain}), independent of user JWTs: no credentials
 *       or a user's JWT (USER or ADMIN) → 401; the correct Basic Auth credential → 200; the
 *       wrong Basic Auth password → 401.</li>
 * </ul>
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
        "planner.metrics.username=prometheus",
        "planner.metrics.password=test-metrics-password",
})
@DisplayName("Actuator endpoint security (native auth)")
class ActuatorSecurityIT {

    private static String basicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlannerStore store;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setup() throws Exception {
        store.resetAllData();
        register("alice", "alice@x.com", "password1");  // first user → ADMIN
        register("bob",   "bob@x.com",   "password1");  // second user → USER
        adminToken = login("alice", "password1");
        userToken  = login("bob",   "password1");
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

    // --- public endpoints must remain accessible without credentials ---

    @Test
    @DisplayName("/actuator/health is public - no token required")
    void health_isPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/info is public - no token required")
    void info_isPublic() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    // --- prometheus is gated by its own Basic Auth credential, not user JWTs ---

    @Test
    @DisplayName("/actuator/prometheus: unauthenticated → 401")
    void prometheus_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/prometheus: a user's JWT means nothing here → 401, even for ADMIN")
    void prometheus_userJwt_returns401() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/prometheus: correct Basic Auth credential → 200")
    void prometheus_correctBasicAuth_returns200() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", basicAuthHeader("prometheus", "test-metrics-password")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/actuator/prometheus: wrong Basic Auth password → 401")
    void prometheus_wrongBasicAuth_returns401() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", basicAuthHeader("prometheus", "not-the-password")))
                .andExpect(status().isUnauthorized());
    }
}
