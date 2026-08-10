package com.lilac.planner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("Task API - HTTP integration")
class TaskControllerIT {

    @Autowired
    MockMvc mvc;

    // Each test uses a distinct future date to avoid shared state within the cached Spring context.

    @Test
    @DisplayName("POST /api/v1/days/{date}/tasks creates a task and returns the updated day")
    void addTask_returnsUpdatedDay() throws Exception {
        mvc.perform(post("/api/v1/days/2099-01-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"standup","points":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("standup"))
                .andExpect(jsonPath("$.tasks[0].points").value(5))
                .andExpect(jsonPath("$.tasks[0].completed").value(false));
    }

    @Test
    @DisplayName("POST with blank title returns 400")
    void addTask_blankTitle_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2099-01-02/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH completes a task and increments totalPoints")
    void patchTask_complete_incrementsPoints() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-01-03/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"workout","points":25}
                                """))
                .andReturn().getResponse().getContentAsString();

        String taskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(patch("/api/v1/days/2099-01-03/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].completed").value(true))
                .andExpect(jsonPath("$.totalPoints").value(25));
    }

    @Test
    @DisplayName("DELETE removes the task from the day")
    void deleteTask_removesFromDay() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-01-04/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"to-delete","points":1}
                                """))
                .andReturn().getResponse().getContentAsString();

        String taskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(delete("/api/v1/days/2099-01-04/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isEmpty());
    }

    @Test
    @DisplayName("DELETE of an unknown task returns 404 with a problem+json body")
    void deleteTask_unknown_returns404() throws Exception {
        mvc.perform(get("/api/v1/days/2099-01-07")) // ensure the day exists
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/days/2099-01-07/tasks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Task not found: 999999"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/days/{date} returns an empty day on first access")
    void getDay_returnsEmptyDay() throws Exception {
        mvc.perform(get("/api/v1/days/2099-01-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2099-01-05"))
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.totalPoints").value(0))
                .andExpect(jsonPath("$.totalAvailablePoints").value(0));
    }

    @Test
    @DisplayName("DELETE on a daily recurring task removes it from all future pre-created days")
    void deleteRecurringTask_removesFromFutureDays() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-02-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"daily standup","points":5,"recurrence":"DAILY"}
                                """))
                .andReturn().getResponse().getContentAsString();

        String taskId = JsonPath.read(body, "$.tasks[0].id");

        // pre-created instance exists on day 2
        mvc.perform(get("/api/v1/days/2099-02-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("daily standup"));

        // delete from day 1
        mvc.perform(delete("/api/v1/days/2099-02-01/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isEmpty());

        // must also be gone from day 2
        mvc.perform(get("/api/v1/days/2099-02-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isEmpty());
    }

    @Test
    @DisplayName("PUT /reorder changes task order")
    void reorderTasks_changesPositions() throws Exception {
        String body1 = mvc.perform(post("/api/v1/days/2099-01-06/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"first","points":1}
                                """))
                .andReturn().getResponse().getContentAsString();

        String body2 = mvc.perform(post("/api/v1/days/2099-01-06/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"second","points":1}
                                """))
                .andReturn().getResponse().getContentAsString();

        String firstId  = JsonPath.read(body1, "$.tasks[0].id");
        String secondId = JsonPath.read(body2, "$.tasks[1].id");

        // Reverse order: second before first
        mvc.perform(put("/api/v1/days/2099-01-06/tasks/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + secondId + "\",\"" + firstId + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("second"))
                .andExpect(jsonPath("$.tasks[1].title").value("first"));
    }
}
