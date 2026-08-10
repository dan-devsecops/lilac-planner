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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("Statistics API - HTTP integration")
class StatisticsControllerIT {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("GET /api/v1/statistics returns empty list when no tasks exist in range")
    void statistics_emptyRange_returnsEmptyList() throws Exception {
        mvc.perform(get("/api/v1/statistics")
                        .param("from", "2099-02-01")
                        .param("to",   "2099-02-07"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("statistics reflect completed tasks after they are added and completed")
    void statistics_afterCompletingTask_showsPoints() throws Exception {
        // Add a task on 2099-02-10
        String body = mvc.perform(post("/api/v1/days/2099-02-10/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"morning run","points":15}
                                """))
                .andReturn().getResponse().getContentAsString();

        String taskId = com.jayway.jsonpath.JsonPath.read(body, "$.tasks[0].id");

        // Complete it
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/days/2099-02-10/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isOk());

        // Query statistics
        mvc.perform(get("/api/v1/statistics")
                        .param("from", "2099-02-10")
                        .param("to",   "2099-02-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points").value(15))
                .andExpect(jsonPath("$[0].completedTasks").value(1))
                .andExpect(jsonPath("$[0].totalTasks").value(1));
    }

    @Test
    @DisplayName("statistics distinguish completed from total tasks")
    void statistics_partialCompletion_distinguishesCompletedFromTotal() throws Exception {
        mvc.perform(post("/api/v1/days/2099-02-11/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"done","points":10}
                                """))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/days/2099-02-11/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"not done","points":5}
                                """))
                .andReturn();

        // Complete only the first task
        String body = mvc.perform(get("/api/v1/days/2099-02-11"))
                .andReturn().getResponse().getContentAsString();
        String firstTaskId = com.jayway.jsonpath.JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/days/2099-02-11/tasks/" + firstTaskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/statistics")
                        .param("from", "2099-02-11")
                        .param("to",   "2099-02-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].completedTasks").value(1))
                .andExpect(jsonPath("$[0].totalTasks").value(2));
    }
}
