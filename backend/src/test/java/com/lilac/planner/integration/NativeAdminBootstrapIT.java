package com.lilac.planner.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * The configured bootstrap admin is created at startup and can immediately sign in with ADMIN role -
 * no public registration race. Verifies the {@link com.lilac.planner.service.AdminBootstrap} runner
 * is wired and active under {@code provider=native}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = {
        "planner.auth.provider=native",
        "planner.auth.native.jwt-secret=test-secret-test-secret-test-secret-1234",
        "planner.auth.native.admin.username=root",
        "planner.auth.native.admin.password=rootpassword1",
        "planner.auth.native.admin.email=root@example.com",
})
@DisplayName("Native bootstrap admin - created at startup")
class NativeAdminBootstrapIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("the bootstrapped admin can log in and is an ADMIN")
    void bootstrapAdminCanLoginAsAdmin() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"root\",\"password\":\"rootpassword1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("root"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }
}
