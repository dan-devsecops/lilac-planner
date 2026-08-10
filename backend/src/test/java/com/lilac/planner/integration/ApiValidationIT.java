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

/**
 * Negative-path coverage: every malformed or invalid request must be rejected
 * with the right 4xx status instead of leaking a 500 or silently mutating data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("API validation - negative paths")
class ApiValidationIT {

    @Autowired
    MockMvc mvc;

    // --- Task payload validation ---

    @Test
    @DisplayName("POST task with negative points returns 400")
    void addTask_negativePoints_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2099-04-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"cheater","points":-5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST task with negative position returns 400")
    void addTask_negativePosition_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2099-04-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"sneaky","position":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH task with negative points returns 400")
    void updateTask_negativePoints_returns400() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-04-02/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"legit","points":1}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(patch("/api/v1/days/2099-04-02/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"points":-3}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST task with an over-long title returns 400")
    void addTask_overlongTitle_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2099-04-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "x".repeat(241) + "\",\"points\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH task with an over-long title returns 400")
    void updateTask_overlongTitle_returns400() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-04-02/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"legit","points":1}
                                """))
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(body, "$.tasks[0].id");

        mvc.perform(patch("/api/v1/days/2099-04-02/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "x".repeat(241) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST task with points above the cap returns 400")
    void addTask_pointsAboveCap_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2099-04-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"jackpot","points":10001}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST task with an unknown recurrence value returns 400")
    void addTask_unknownRecurrence_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2099-04-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","recurrence":"FORTNIGHTLY"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST task with malformed JSON returns 400")
    void addTask_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2099-04-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // --- Path and query parameter validation ---

    @Test
    @DisplayName("GET day with a malformed date returns 400")
    void getDay_malformedDate_returns400() throws Exception {
        mvc.perform(get("/api/v1/days/not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET statistics with a malformed date returns 400")
    void statistics_malformedDate_returns400() throws Exception {
        mvc.perform(get("/api/v1/statistics?from=2099-01-01&to=bad-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET statistics without required params returns 400")
    void statistics_missingParams_returns400() throws Exception {
        mvc.perform(get("/api/v1/statistics"))
                .andExpect(status().isBadRequest());
    }

    // --- Date window bounds (1900-01-01..2100-12-31) ---

    @Test
    @DisplayName("GET day before 1900 returns 400 (and creates nothing)")
    void getDay_before1900_returns400() throws Exception {
        mvc.perform(get("/api/v1/days/1899-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET day after 2100 returns 400 (and creates nothing)")
    void getDay_after2100_returns400() throws Exception {
        mvc.perform(get("/api/v1/days/2101-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET day in the far future (year +999999999) returns 400, not 500")
    void getDay_farFuture_returns400() throws Exception {
        mvc.perform(get("/api/v1/days/+999999999-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST task on an out-of-window date returns 400")
    void addTask_outOfWindowDate_returns400() throws Exception {
        mvc.perform(post("/api/v1/days/2101-01-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"squatter","points":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET statistics with an out-of-window date returns 400")
    void statistics_outOfWindowDate_returns400() throws Exception {
        mvc.perform(get("/api/v1/statistics?from=1899-01-01&to=1899-12-31"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/statistics?from=2099-01-01&to=2101-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET statistics with from after to returns 400")
    void statistics_fromAfterTo_returns400() throws Exception {
        mvc.perform(get("/api/v1/statistics?from=2099-02-01&to=2099-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET statistics over a range longer than 5 years returns 400")
    void statistics_rangeOverFiveYears_returns400() throws Exception {
        mvc.perform(get("/api/v1/statistics?from=2050-01-01&to=2055-01-02"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET statistics over a range of exactly 5 years is accepted")
    void statistics_rangeExactlyFiveYears_returns200() throws Exception {
        mvc.perform(get("/api/v1/statistics?from=2050-01-01&to=2055-01-01"))
                .andExpect(status().isOk());
    }

    // --- Reorder payload bounds ---

    @Test
    @DisplayName("PUT reorder with more than 500 ids returns 400")
    void reorder_oversizedList_returns400() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 501)
                .mapToObj(i -> "\"id-" + i + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        mvc.perform(put("/api/v1/days/2099-04-05/tasks/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ids))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT reorder with an over-long task id returns 400")
    void reorder_overlongId_returns400() throws Exception {
        mvc.perform(put("/api/v1/days/2099-04-05/tasks/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + "x".repeat(65) + "\"]"))
                .andExpect(status().isBadRequest());
    }

    // --- Unknown resources ---

    @Test
    @DisplayName("PATCH of an unknown task returns 404")
    void updateTask_unknown_returns404() throws Exception {
        mvc.perform(get("/api/v1/days/2099-04-03")) // ensure the day exists
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/days/2099-04-03/tasks/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT reorder on a day that does not exist returns 404")
    void reorder_unknownDay_returns404() throws Exception {
        mvc.perform(put("/api/v1/days/2099-04-04/tasks/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"1\"]"))
                .andExpect(status().isNotFound());
    }

    // --- Error body contract (RFC 9457 problem+json) ---

    @Test
    @DisplayName("validation failures render as application/problem+json")
    void validationError_rendersProblemJson() throws Exception {
        mvc.perform(post("/api/v1/days/2099-04-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","points":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("malformed path parameters render as application/problem+json")
    void typeMismatch_rendersProblemJson() throws Exception {
        mvc.perform(get("/api/v1/days/not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'date'"));
    }

    // --- User payload validation ---

    @Test
    @DisplayName("POST user with a blank username returns 400")
    void createUser_blankUsername_returns400() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST user with an over-long username returns 400")
    void createUser_overlongUsername_returns400() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + "x".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
