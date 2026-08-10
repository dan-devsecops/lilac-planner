package com.lilac.planner.unit;

import com.lilac.planner.dto.StatPointDto;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.Task;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.StatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsService - range aggregation")
class StatisticsServiceUnitTest {

    @Mock PlannerStore store;
    @InjectMocks StatisticsService stats;

    static final LocalDate FROM = LocalDate.of(2099, 4, 1);
    static final LocalDate TO   = LocalDate.of(2099, 4, 7);

    @Test
    @DisplayName("returns empty list when no days exist in range")
    void range_noDays_returnsEmpty() {
        when(store.findDaysInRange("u1", FROM, TO)).thenReturn(List.of());
        assertThat(stats.range("u1", FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("maps each day to a StatPointDto with correct point and task counts")
    void range_mapsCompletedTasksCorrectly() {
        Day day = new Day("u1", FROM);
        Task done = new Task("done", 10, 0); done.setCompleted(true);
        Task open = new Task("open", 5, 1);
        day.getTasks().addAll(List.of(done, open));

        when(store.findDaysInRange("u1", FROM, FROM)).thenReturn(List.of(day));

        List<StatPointDto> result = stats.range("u1", FROM, FROM);

        assertThat(result).hasSize(1);
        StatPointDto dto = result.get(0);
        assertThat(dto.date()).isEqualTo(FROM);
        assertThat(dto.points()).isEqualTo(10);
        assertThat(dto.completedTasks()).isEqualTo(1);
        assertThat(dto.totalTasks()).isEqualTo(2);
    }

    @Test
    @DisplayName("day with all tasks completed yields points equal to sum of all task points")
    void range_allCompleted_pointsMatchTotal() {
        Day day = new Day("u1", FROM);
        for (int i = 0; i < 3; i++) {
            Task t = new Task("t" + i, 5, i);
            t.setCompleted(true);
            day.getTasks().add(t);
        }

        when(store.findDaysInRange("u1", FROM, FROM)).thenReturn(List.of(day));

        StatPointDto dto = stats.range("u1", FROM, FROM).get(0);
        assertThat(dto.points()).isEqualTo(15);
        assertThat(dto.completedTasks()).isEqualTo(3);
        assertThat(dto.totalTasks()).isEqualTo(3);
    }

    @Test
    @DisplayName("day with no completed tasks has zero points")
    void range_noneCompleted_zeroPoints() {
        Day day = new Day("u1", FROM);
        day.getTasks().add(new Task("open", 20, 0));

        when(store.findDaysInRange("u1", FROM, FROM)).thenReturn(List.of(day));

        StatPointDto dto = stats.range("u1", FROM, FROM).get(0);
        assertThat(dto.points()).isZero();
        assertThat(dto.completedTasks()).isZero();
        assertThat(dto.totalTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("multiple days are all returned and mapped independently")
    void range_multipleDays_mappedSeparately() {
        Day d1 = new Day("u1", FROM);
        Task t1 = new Task("a", 3, 0); t1.setCompleted(true);
        d1.getTasks().add(t1);

        Day d2 = new Day("u1", FROM.plusDays(1));
        d2.getTasks().add(new Task("b", 7, 0));

        when(store.findDaysInRange("u1", FROM, TO)).thenReturn(List.of(d1, d2));

        List<StatPointDto> result = stats.range("u1", FROM, TO);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).points()).isEqualTo(3);
        assertThat(result.get(1).points()).isZero();
    }
}
