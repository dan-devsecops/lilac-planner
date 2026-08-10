package com.lilac.planner.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * User API in {@code AUTH_PROVIDER=none} mode. Only {@code /api/v1/users/me} is reachable here:
 * listing and creating users require {@code ROLE_ADMIN}, and with auth disabled there is no
 * principal at all, so those endpoints are intentionally inaccessible (403).
 * Admin happy paths are covered in {@link UserControllerAdminIT} with native auth.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("User API - HTTP integration (auth disabled)")
class UserControllerIT {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("GET /api/v1/users/me returns the dev user when auth is disabled")
    void me_returnsDevUser() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("dev"))
                .andExpect(jsonPath("$.displayName").value("Dev User"));
    }

    @Test
    @DisplayName("GET /api/v1/users is denied when there is no admin principal")
    void listUsers_withoutAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/users is denied when there is no admin principal")
    void createUser_withoutAdmin_isForbidden() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"charlie","displayName":"Charlie Brown"}
                                """))
                .andExpect(status().isForbidden());
    }
}
