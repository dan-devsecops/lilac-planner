package com.lilac.planner.unit;

import com.lilac.planner.dto.DayDto;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DayDto - view-model derivation")
class DayDtoUnitTest {

    @Test
    @DisplayName("DayDto.from sorts tasks by their position field")
    void tasksAreSortedByPosition() {
        Day d = new Day("u1", LocalDate.of(2026, 1, 1));
        d.getTasks().add(taskWithPosition("third", 2));
        d.getTasks().add(taskWithPosition("first", 0));
        d.getTasks().add(taskWithPosition("second", 1));

        DayDto dto = DayDto.from(d);

        assertThat(dto.tasks()).extracting("title")
                .containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("DayDto exposes both totalPoints and totalAvailablePoints")
    void totalsExposed() {
        Day d = new Day("u1", LocalDate.of(2026, 1, 1));
        Task done = new Task("a", 5, 0); done.setCompleted(true);
        d.getTasks().add(done);
        d.getTasks().add(new Task("b", 99, 1));

        DayDto dto = DayDto.from(d);

        assertThat(dto.totalPoints()).isEqualTo(5);
        assertThat(dto.totalAvailablePoints()).isEqualTo(104);
    }

    @Test
    @DisplayName("earned-stickers list is preserved on the DTO")
    void earnedStickersPreserved() {
        Day d = new Day("u1", LocalDate.of(2026, 1, 1));
        d.setEarnedStickers(List.of("kitty", "bunny"));

        assertThat(DayDto.from(d).earnedStickers()).containsExactly("kitty", "bunny");
    }

    @Test
    @DisplayName("DayDto carries the userId through")
    void userIdPreserved() {
        Day d = new Day("user-42", LocalDate.of(2026, 1, 1));
        assertThat(DayDto.from(d).userId()).isEqualTo("user-42");
    }

    private static Task taskWithPosition(String title, int position) {
        return new Task(title, 1, position);
    }
}
