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
        "planner.auth.enabled=false",
        "planner.mobile.min-supported-app-version=1.2.0",
        "planner.mobile.latest-app-version=1.5.0",
})
@DisplayName("App-version gate - GET /api/v1/meta (auth provider = none)")
class MetaControllerIT {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("returns the configured versions without authentication")
    void returnsVersions() throws Exception {
        mvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minSupportedAppVersion").value("1.2.0"))
                .andExpect(jsonPath("$.latestAppVersion").value("1.5.0"));
    }
}
