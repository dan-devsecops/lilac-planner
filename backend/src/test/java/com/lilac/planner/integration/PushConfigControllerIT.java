package com.lilac.planner.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = {
        "planner.auth.provider=native",
        "planner.auth.native.jwt-secret=test-secret-test-secret-test-secret-1234",
})
@DisplayName("GET /api/v1/push/vapid-public-key (auth provider = native, VAPID unset)")
class PushConfigControllerIT {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("is reachable without a Bearer token and degrades to an empty key, not a 500")
    void publicWithoutToken_andGracefullyEmpty() throws Exception {
        mvc.perform(get("/api/v1/push/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value(""));
    }

    @Test
    @DisplayName("a request without a token to a real protected endpoint still 401s (this isn't a blanket bypass)")
    void otherEndpointsStillRequireAuth() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
