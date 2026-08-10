package com.lilac.planner.unit;

import com.lilac.planner.model.Day;
import com.lilac.planner.model.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Day - domain logic")
class DayUnitTest {

    @Test
    @DisplayName("empty day has zero earned and zero available points")
    void emptyDay() {
        Day d = new Day("u1", LocalDate.of(2026, 5, 12));
        assertThat(d.totalPoints()).isZero();
        assertThat(d.totalAvailablePoints()).isZero();
    }

    @Test
    @DisplayName("totalPoints sums only completed tasks")
    void totalPointsCountsOnlyCompleted() {
        Day d = new Day("u1", LocalDate.of(2026, 5, 12));
        d.getTasks().add(completed("done-1", 5));
        d.getTasks().add(notCompleted("open-1", 7));
        d.getTasks().add(completed("done-2", 10));

        assertThat(d.totalPoints()).isEqualTo(15);
    }

    @Test
    @DisplayName("totalAvailablePoints sums every task regardless of state")
    void totalAvailablePointsCountsAll() {
        Day d = new Day("u1", LocalDate.of(2026, 5, 12));
        d.getTasks().add(completed("a", 5));
        d.getTasks().add(notCompleted("b", 7));

        assertThat(d.totalAvailablePoints()).isEqualTo(12);
    }

    @Test
    @DisplayName("uncompleting a task removes its points from the running total")
    void uncompletingDropsPoints() {
        Day d = new Day("u1", LocalDate.of(2026, 5, 12));
        Task t = completed("yoga", 8);
        d.getTasks().add(t);
        assertThat(d.totalPoints()).isEqualTo(8);

        t.setCompleted(false);
        assertThat(d.totalPoints()).isZero();
    }

    private static Task completed(String title, int points) {
        Task t = new Task(title, points, 0);
        t.setCompleted(true);
        return t;
    }

    private static Task notCompleted(String title, int points) {
        return new Task(title, points, 0);
    }
}
