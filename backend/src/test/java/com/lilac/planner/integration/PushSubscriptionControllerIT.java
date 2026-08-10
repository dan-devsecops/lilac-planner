package com.lilac.planner.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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

import java.util.LinkedHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = {
        "planner.auth.provider=native",
        "planner.auth.native.jwt-secret=test-secret-test-secret-test-secret-1234",
        "planner.auth.native.access-ttl=PT15M",
        "planner.rate-limit.login-per-minute=10000",
        "planner.rate-limit.register-per-minute=10000",
        "planner.push.vapid.public-key=test-vapid-public-key",
})
@DisplayName("Push subscription + timezone API - GET/POST/DELETE /api/v1/me/push-subscriptions, PATCH /api/v1/me/timezone")
class PushSubscriptionControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlannerStore store;

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setup() throws Exception {
        store.resetAllData();
        register("alice", "alice@example.com", "password1");
        register("bob",   "bob@example.com",   "password1");
        aliceToken = login("alice", "password1");
        bobToken   = login("bob",   "password1");
    }

    @Test
    @DisplayName("registers a subscription and returns it without echoing the token")
    void register_returnsDtoWithoutRawToken() throws Exception {
        mvc.perform(post("/api/v1/me/push-subscriptions")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platform":"WEB","token":"secret-endpoint","p256dh":"p-key","auth":"a-key"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.platform").value("WEB"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.lastSeenAt").exists())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.p256dh").doesNotExist())
                .andExpect(jsonPath("$.auth").doesNotExist());
    }

    @Test
    @DisplayName("registering the same token twice upserts instead of duplicating")
    void register_sameTokenTwice_upserts() throws Exception {
        register(aliceToken, "EXPO", "expo-token-1", null, null);
        register(aliceToken, "EXPO", "expo-token-1", null, null);

        mvc.perform(get("/api/v1/me/push-subscriptions").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("lists only the authenticated user's own subscriptions")
    void list_scopedToAuthenticatedUser() throws Exception {
        register(aliceToken, "EXPO", "alice-token", null, null);
        register(bobToken, "EXPO", "bob-token", null, null);

        mvc.perform(get("/api/v1/me/push-subscriptions").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].platform").value("EXPO"));

        mvc.perform(get("/api/v1/me/push-subscriptions").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("deletes a subscription owned by the authenticated user")
    void delete_ownSubscription_succeeds() throws Exception {
        String id = register(aliceToken, "EXPO", "alice-token", null, null);

        mvc.perform(delete("/api/v1/me/push-subscriptions/" + id).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me/push-subscriptions").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Bob cannot delete Alice's subscription (404, not 200)")
    void delete_anotherUsersSubscription_isRejected() throws Exception {
        String aliceSubId = register(aliceToken, "EXPO", "alice-token", null, null);

        mvc.perform(delete("/api/v1/me/push-subscriptions/" + aliceSubId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/me/push-subscriptions").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("deleting an unknown id returns 404")
    void delete_unknownId_returns404() throws Exception {
        mvc.perform(delete("/api/v1/me/push-subscriptions/does-not-exist")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("registering without a bearer token is unauthorized")
    void register_withoutToken_isUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/me/push-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platform":"WEB","token":"secret-endpoint"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/v1/me/timezone updates the authenticated user's timezone")
    void updateTimezone_succeeds() throws Exception {
        mvc.perform(patch("/api/v1/me/timezone")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timezone":"Europe/Prague"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/v1/me/timezone rejects an invalid IANA zone id")
    void updateTimezone_invalidZone_isRejected() throws Exception {
        mvc.perform(patch("/api/v1/me/timezone")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timezone":"Not/AZone"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/push/vapid-public-key is public and returns the configured key")
    void vapidPublicKey_isPublicAndReturnsConfiguredKey() throws Exception {
        mvc.perform(get("/api/v1/push/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("test-vapid-public-key"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String register(String token, String platform, String pushToken, String p256dh, String auth)
            throws Exception {
        var body = new LinkedHashMap<String, String>();
        body.put("platform", platform);
        body.put("token", pushToken);
        if (p256dh != null) body.put("p256dh", p256dh);
        if (auth != null) body.put("auth", auth);
        MvcResult res = mvc.perform(post("/api/v1/me/push-subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private void register(String username, String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LinkedHashMap<>() {{
                            put("username", username);
                            put("email", email);
                            put("displayName", username);
                            put("password", password);
                        }})))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
