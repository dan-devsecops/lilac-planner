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
        "planner.mobile.min-supported-app-version=2.0.0",
        "planner.mobile.latest-app-version=2.1.0",
})
@DisplayName("App-version gate - GET /api/v1/meta (auth provider = native)")
class MetaControllerNativeAuthIT {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("is reachable without a Bearer token")
    void publicWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minSupportedAppVersion").value("2.0.0"))
                .andExpect(jsonPath("$.latestAppVersion").value("2.1.0"));
    }

    @Test
    @DisplayName("a request without a token to a real protected endpoint still 401s (meta isn't a blanket bypass)")
    void otherEndpointsStillRequireAuth() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
