package com.lilac.planner.integration;

import com.lilac.planner.service.ConcurrentUpdateException;
import com.lilac.planner.service.PlannerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A lost-update race must surface to the client as 409 Conflict - never as a 500.
 * Covers both shapes the conflict can take: the adapters' own
 * {@link ConcurrentUpdateException}, and a raw Spring
 * {@link org.springframework.dao.OptimisticLockingFailureException} that escapes a
 * {@code @Transactional} service method at commit flush (JPA's version-checked
 * UPDATE runs after {@code saveDay} has already returned).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("Concurrent updates map to 409 Conflict")
class ConcurrencyConflictIT {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlannerService planner;

    @Test
    @DisplayName("ConcurrentUpdateException from an adapter renders as 409 problem+json")
    void concurrentUpdateException_returns409() throws Exception {
        when(planner.updateTask(anyString(), any(LocalDate.class), anyString(), any()))
                .thenThrow(new ConcurrentUpdateException("Day was modified concurrently - please retry: 2099-05-01"));

        mvc.perform(patch("/api/v1/days/2099-05-01/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Concurrent update conflict"));
    }

    @Test
    @DisplayName("OptimisticLockingFailureException escaping the service renders as 409, not 500")
    void optimisticLockingFailure_returns409() throws Exception {
        when(planner.updateTask(anyString(), any(LocalDate.class), anyString(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("Day", 1L));

        mvc.perform(patch("/api/v1/days/2099-05-01/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Concurrent update conflict"));
    }
}
