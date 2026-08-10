package com.lilac.planner.unit;

import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.model.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task - new optional fields")
class TaskUnitTest {

    @Test
    @DisplayName("a freshly constructed Task has no scheduled time and recurrence NONE")
    void defaultsForNewTask() {
        Task t = new Task("read", 3, 0);

        assertThat(t.getScheduledTime()).isNull();
        assertThat(t.getRecurrence()).isEqualTo(Recurrence.NONE);
        assertThat(t.getRecurrenceGroupId()).isNull();
    }

    @Test
    @DisplayName("setting recurrence to null is normalised back to NONE")
    void nullRecurrenceFallsBackToNone() {
        Task t = new Task("read", 3, 0);
        t.setRecurrence(null);

        assertThat(t.getRecurrence()).isEqualTo(Recurrence.NONE);
    }

    @Test
    @DisplayName("scheduledTime and recurrence round-trip through setters")
    void roundTripOfOptionalFields() {
        Task t = new Task("standup", 2, 0);
        t.setScheduledTime(LocalTime.of(9, 30));
        t.setRecurrence(Recurrence.WEEKLY);
        t.setRecurrenceGroupId("group-abc");

        assertThat(t.getScheduledTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(t.getRecurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(t.getRecurrenceGroupId()).isEqualTo("group-abc");
    }
}
