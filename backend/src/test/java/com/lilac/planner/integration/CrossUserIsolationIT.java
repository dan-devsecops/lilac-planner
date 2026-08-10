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

/**
 * Verifies that user data is fully isolated: one authenticated user cannot read,
 * modify, or delete another user's days, tasks, or statistics.
 *
 * Uses native auth so each request carries a distinct JWT; alice and bob are
 * separate users backed by the same H2 database.
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
@DisplayName("Cross-user data isolation")
class CrossUserIsolationIT {

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

    // -------------------------------------------------------------------------
    // DayController - GET /api/v1/days/{date}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bob sees an empty day even when Alice has tasks on the same date")
    void getDay_doesNotLeakAcrossUsers() throws Exception {
        mvc.perform(post("/api/v1/days/2099-09-01/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"alice task","points":5}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/days/2099-09-01")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isEmpty());
    }

    @Test
    @DisplayName("Bob cannot read Alice's task; Alice can read her own task")
    void getTask_individualEndpointDoesNotExist() throws Exception {
        mvc.perform(post("/api/v1/days/2099-09-02/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"alice task","points":3}
                                """))
                .andExpect(status().isOk());

        // Task data is exposed through GET /api/v1/days/{date}, not a dedicated GET /tasks/{id}.
        // Bob's view of the same date must be empty - Alice's task is not visible to him.
        mvc.perform(get("/api/v1/days/2099-09-02")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isEmpty());

        // Alice can see her own task on that date.
        mvc.perform(get("/api/v1/days/2099-09-02")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("alice task"));
    }

    // -------------------------------------------------------------------------
    // TaskController - PATCH /api/v1/days/{date}/tasks/{taskId}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bob cannot complete a task that belongs to Alice")
    void patchTask_cannotCompleteAnotherUsersTask() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-09-02/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"alice task","points":3}
                                """))
                .andReturn().getResponse().getContentAsString();

        String aliceTaskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(patch("/api/v1/days/2099-09-02/tasks/" + aliceTaskId)
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bob cannot rename a task that belongs to Alice")
    void patchTask_cannotRenameAnotherUsersTask() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-09-03/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"alice task","points":3}
                                """))
                .andReturn().getResponse().getContentAsString();

        String aliceTaskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(patch("/api/v1/days/2099-09-03/tasks/" + aliceTaskId)
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"hijacked"}
                                """))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // TaskController - DELETE /api/v1/days/{date}/tasks/{taskId}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bob cannot delete a task that belongs to Alice")
    void deleteTask_cannotDeleteAnotherUsersTask() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-09-04/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"alice task","points":3}
                                """))
                .andReturn().getResponse().getContentAsString();

        String aliceTaskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(delete("/api/v1/days/2099-09-04/tasks/" + aliceTaskId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());

        // Alice's task must still be there
        mvc.perform(get("/api/v1/days/2099-09-04")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("alice task"));
    }

    // -------------------------------------------------------------------------
    // TaskController - PUT /api/v1/days/{date}/tasks/reorder
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bob cannot reorder Alice's tasks and Alice's order is unchanged")
    void reorderTasks_cannotReorderAnotherUsersDay() throws Exception {
        String body1 = mvc.perform(post("/api/v1/days/2099-09-05/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"first","points":1}
                                """))
                .andReturn().getResponse().getContentAsString();

        String body2 = mvc.perform(post("/api/v1/days/2099-09-05/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"second","points":1}
                                """))
                .andReturn().getResponse().getContentAsString();

        String firstId  = JsonPath.read(body1, "$.tasks[0].id");
        String secondId = JsonPath.read(body2, "$.tasks[1].id");

        // Bob submits Alice's task IDs - Bob has no day here so the server returns 404,
        // not Alice's data
        mvc.perform(put("/api/v1/days/2099-09-05/tasks/reorder")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + secondId + "\",\"" + firstId + "\"]"))
                .andExpect(status().isNotFound());

        // Alice's order is unchanged
        mvc.perform(get("/api/v1/days/2099-09-05")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("first"))
                .andExpect(jsonPath("$.tasks[1].title").value("second"));
    }

    // -------------------------------------------------------------------------
    // StatisticsController - GET /api/v1/statistics
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bob's statistics are empty even when Alice has completed tasks in the same range")
    void statistics_doesNotLeakAcrossUsers() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-09-06/tasks")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"alice workout","points":20}
                                """))
                .andReturn().getResponse().getContentAsString();

        String aliceTaskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(patch("/api/v1/days/2099-09-06/tasks/" + aliceTaskId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/statistics")
                        .header("Authorization", "Bearer " + bobToken)
                        .param("from", "2099-09-06")
                        .param("to",   "2099-09-06"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // -------------------------------------------------------------------------
    // UserController - GET /api/v1/users/me
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("/api/v1/users/me returns each user's own profile, not another user's")
    void usersMe_returnsOwnProfile() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"));
    }

    // -------------------------------------------------------------------------
    // UserController - GET /api/v1/users (user enumeration)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bob (plain USER) cannot list all users and discover that Alice exists")
    void listUsers_plainUserCannotEnumerateOtherUsers() throws Exception {
        // Alice registered first and became ADMIN; Bob is a plain USER.
        // Bob must not be able to call GET /api/v1/users to learn that Alice exists.
        mvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("unauthenticated request cannot enumerate users")
    void listUsers_unauthenticatedCannotEnumerateUsers() throws Exception {
        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bob's token returns only Bob's own profile, not Alice's")
    void usersMe_returnsOnlyOwnIdentity() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.username").value(org.hamcrest.Matchers.not("alice")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
