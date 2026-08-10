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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("Recurrence - HTTP integration")
class RecurrenceIT {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("a DAILY recurring task appears on the next 3 days")
    void dailyTask_appearsOnFutureDays() throws Exception {
        mvc.perform(post("/api/v1/days/2099-03-01/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"morning pages","points":3,"recurrence":"DAILY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].recurrence").value("DAILY"));

        for (int i = 1; i <= 3; i++) {
            String date = "2099-03-%02d".formatted(i + 1);
            mvc.perform(get("/api/v1/days/" + date))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tasks[?(@.title=='morning pages')]").isNotEmpty())
                    .andExpect(jsonPath("$.tasks[?(@.recurrence=='DAILY')]").isNotEmpty());
        }
    }

    @Test
    @DisplayName("all recurring instances share the same recurrenceGroupId")
    void dailyTask_instancesShareGroupId() throws Exception {
        String body = mvc.perform(post("/api/v1/days/2099-03-10/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"yoga","points":5,"recurrence":"DAILY"}
                                """))
                .andReturn().getResponse().getContentAsString();

        // Filter by title to avoid picking up tasks from other tests sharing this date
        java.util.List<String> createdIds = JsonPath.read(body, "$.tasks[?(@.title=='yoga')].recurrenceGroupId");
        String groupId = createdIds.get(0);

        String nextDay = mvc.perform(get("/api/v1/days/2099-03-11"))
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> nextIds = JsonPath.read(nextDay, "$.tasks[?(@.title=='yoga')].recurrenceGroupId");
        org.assertj.core.api.Assertions.assertThat(nextIds).hasSize(1).containsOnly(groupId);
    }

    @Test
    @DisplayName("a WEEKLY recurring task appears one and two weeks later")
    void weeklyTask_appearsOnFutureWeeks() throws Exception {
        mvc.perform(post("/api/v1/days/2099-03-20/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"weekly review","points":10,"recurrence":"WEEKLY"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/days/2099-03-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.title=='weekly review')]").isNotEmpty());

        mvc.perform(get("/api/v1/days/2099-04-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.title=='weekly review')]").isNotEmpty());
    }

    @Test
    @DisplayName("a non-recurring task does NOT appear on the next day")
    void nonRecurringTask_doesNotAppearNextDay() throws Exception {
        mvc.perform(post("/api/v1/days/2099-03-05/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"one-off task","points":1}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/days/2099-03-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.title=='one-off task')]").isEmpty());
    }
}
