package com.lilac.planner.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end native-auth flow over HTTP on H2: register → login → access a protected endpoint →
 * refresh → change password → forgot/reset → admin reset. Runs with {@code provider=native} and a
 * test signing secret. {@link MailService} is mocked so the reset link can be captured.
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
        "planner.rate-limit.forgot-per-minute=10000",
})
@DisplayName("Native auth - HTTP integration")
class NativeAuthIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlannerStore store;
    @MockBean MailService mailService;

    @BeforeEach
    void clean() {
        store.resetAllData();
    }

    private MvcResult register(String username, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("username", username);
                            put("email", email);
                            put("displayName", username);
                            put("password", password);
                        }})))
                .andReturn();
    }

    private String login(String login, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private MvcResult loginResult(String login, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Extracts the refresh-token value from the Set-Cookie response header. */
    private String refreshTokenCookie(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            part = part.trim();
            if (part.startsWith("refresh_token=")) {
                return part.substring("refresh_token=".length());
            }
        }
        return null;
    }

    @Test
    @DisplayName("first user becomes ADMIN, can log in and reach a protected endpoint; no token → 401")
    void firstUserIsAdmin_andProtectedAccess() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@x.com\",\"displayName\":\"Alice\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        String token = login("alice", "password1");

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));

        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("second user is USER; refresh rotates and invalidates the old refresh token")
    void secondUserIsUser_refreshRotates() throws Exception {
        register("alice", "alice@x.com", "password1");
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"bob@x.com\",\"displayName\":\"Bob\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        MvcResult loginRes = loginResult("bob", "password1");
        String oldRefresh = refreshTokenCookie(loginRes);

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // the old (rotated) refresh token is now rejected
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("duplicate registration → 409; wrong password → 401")
    void duplicateAndBadCredentials() throws Exception {
        register("alice", "alice@x.com", "password1");
        register("bob", "alice@x.com", "password1");  // duplicate email
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\",\"email\":\"alice@x.com\",\"displayName\":\"Carol\",\"password\":\"password1\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("change-password: old password stops working, new one works")
    void changePassword() throws Exception {
        register("alice", "alice@x.com", "password1");
        String token = login("alice", "password1");

        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password1\",\"newPassword\":\"password2\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized());
        login("alice", "password2");  // new password works (asserts 200 inside)
    }

    @Test
    @DisplayName("forgot-password emails a link; the token resets the password")
    void forgotAndReset() throws Exception {
        register("alice", "alice@x.com", "password1");

        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@x.com\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordReset(eq("alice@x.com"), link.capture());
        String resetToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"password3\"}"))
                .andExpect(status().isNoContent());

        login("alice", "password3");  // reset password works
    }

    @Test
    @DisplayName("admin reset: non-admin gets 403; admin resets a user's password")
    void adminReset_authorization() throws Exception {
        register("alice", "alice@x.com", "password1");        // ADMIN
        register("bob", "bob@x.com", "password1");            // USER
        String adminToken = login("alice", "password1");
        String userToken = login("bob", "password1");

        // a USER cannot reset another user's password
        mvc.perform(post("/api/v1/auth/admin/reset-password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"newPassword\":\"password9\"}"))
                .andExpect(status().isForbidden());

        // an ADMIN can
        mvc.perform(post("/api/v1/auth/admin/reset-password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"newPassword\":\"password9\"}"))
                .andExpect(status().isNoContent());

        login("bob", "password9");  // bob's new password works
    }

    // --- HttpOnly cookie tests ---

    @Test
    @DisplayName("login: access token in body only; refresh token in HttpOnly cookie with correct attributes")
    void login_setsHttpOnlyCookieNotInBody() throws Exception {
        register("alice", "alice@x.com", "password1");

        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = res.getResponse().getHeader("Set-Cookie");
        assertThat(refreshTokenCookie(res)).isNotBlank();
        assertThat(setCookie)
                .isNotNull()
                .contains("HttpOnly")
                .containsIgnoringCase("SameSite=Strict")
                .containsIgnoringCase("Path=/api/v1/auth")
                .contains("Secure");
    }

    @Test
    @DisplayName("refresh: no cookie → 401")
    void refresh_noCookie_returns401() throws Exception {
        register("alice", "alice@x.com", "password1");

        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh: invalid cookie value → 401")
    void refresh_invalidCookie_returns401() throws Exception {
        register("alice", "alice@x.com", "password1");

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "not-a-valid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh: response sets a new cookie with a different token value")
    void refresh_setsNewCookie() throws Exception {
        register("alice", "alice@x.com", "password1");
        MvcResult loginRes = loginResult("alice", "password1");
        String oldToken = refreshTokenCookie(loginRes);

        MvcResult refreshRes = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String newToken = refreshTokenCookie(refreshRes);
        assertThat(newToken).isNotBlank().isNotEqualTo(oldToken);
    }

    @Test
    @DisplayName("logout: clears the cookie (Max-Age=0) and invalidates the token")
    void logout_clearsCookieAndInvalidatesToken() throws Exception {
        register("alice", "alice@x.com", "password1");
        MvcResult loginRes = loginResult("alice", "password1");
        String refreshToken = refreshTokenCookie(loginRes);

        MvcResult logoutRes = mvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = logoutRes.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull().contains("Max-Age=0").contains("HttpOnly");

        // the now-invalidated token must be rejected
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout: without cookie is a no-op (idempotent)")
    void logout_withoutCookie_isIdempotent() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // --- Mobile (body-based) mode ---

    @Test
    @DisplayName("mobile login: response body includes the refresh token; cookie is still set")
    void mobileLogin_returnsRefreshTokenInBody() throws Exception {
        register("alice", "alice@x.com", "password1");

        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"password1\",\"client\":\"mobile\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        assertThat(refreshTokenCookie(res)).isNotBlank();
    }

    @Test
    @DisplayName("mobile refresh: body-provided refresh token is accepted and rotated in the body")
    void mobileRefresh_bodyTokenRotates() throws Exception {
        register("alice", "alice@x.com", "password1");
        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"password1\",\"client\":\"mobile\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String oldRefresh = json.readTree(loginRes.getResponse().getContentAsString()).get("refreshToken").asText();

        MvcResult refreshRes = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        String newRefresh = json.readTree(refreshRes.getResponse().getContentAsString()).get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // the old (rotated) token is now rejected, whether presented via body or cookie
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("mobile logout: body-provided refresh token is revoked even with no cookie present")
    void mobileLogout_bodyTokenRevoked() throws Exception {
        register("alice", "alice@x.com", "password1");
        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"password1\",\"client\":\"mobile\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = json.readTree(loginRes.getResponse().getContentAsString()).get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("web login (no client field): refresh token absent from body, matching cookie-only contract")
    void webLogin_omitsRefreshTokenField() throws Exception {
        register("alice", "alice@x.com", "password1");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"alice\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("concurrent registrations on a fresh install: exactly one user becomes ADMIN")
    void concurrentRegistrations_exactlyOneAdmin() throws Exception {
        int N = 6;
        CountDownLatch ready = new CountDownLatch(N);
        CountDownLatch go    = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            String username = "user" + i;
            String email    = username + "@x.com";
            threads.add(new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    mvc.perform(post("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json.writeValueAsString(new java.util.LinkedHashMap<>() {{
                                        put("username", username);
                                        put("email", email);
                                        put("displayName", username);
                                        put("password", "password1");
                                    }})))
                            .andReturn();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        threads.forEach(Thread::start);
        ready.await();
        go.countDown();
        for (Thread t : threads) t.join(10_000);

        long adminCount = store.listUsers().stream()
                .filter(u -> u.hasRole(com.lilac.planner.domain.Role.ADMIN))
                .count();
        org.assertj.core.api.Assertions.assertThat(adminCount)
                .as("exactly one user must be ADMIN regardless of registration race")
                .isEqualTo(1);
    }
}
