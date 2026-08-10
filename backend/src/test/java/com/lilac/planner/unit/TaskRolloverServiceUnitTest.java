package com.lilac.planner.unit;

import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.TaskRolloverService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskRolloverService - midnight rollover")
class TaskRolloverServiceUnitTest {

    @Mock PlannerStore store;
    @InjectMocks TaskRolloverService service;

    static final String USER = "u1";
    static final LocalDate FROM = LocalDate.of(2026, 6, 7);
    static final LocalDate TO   = LocalDate.of(2026, 6, 8);

    @Test
    @DisplayName("incomplete non-recurring tasks are copied to the next day")
    void rollover_incompleteNonRecurring_copied() {
        Task t = task("buy milk", 3, false, Recurrence.NONE);
        Day yesterday = dayWith(FROM, t);
        Day today = new Day(USER, TO);

        when(store.findDay(USER, FROM)).thenReturn(Optional.of(yesterday));
        when(store.getOrCreateDay(USER, TO)).thenReturn(today);
        when(store.saveDay(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rolloverForUser(USER, FROM, TO);

        ArgumentCaptor<Day> saved = ArgumentCaptor.forClass(Day.class);
        verify(store).saveDay(saved.capture());
        List<Task> tasks = saved.getValue().getTasks();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTitle()).isEqualTo("buy milk (moved)");
        assertThat(tasks.get(0).getPoints()).isEqualTo(3);
        assertThat(tasks.get(0).isCompleted()).isFalse();
    }

    @Test
    @DisplayName("completed tasks are not rolled over")
    void rollover_completedTask_skipped() {
        Task t = task("done already", 1, true, Recurrence.NONE);
        when(store.findDay(USER, FROM)).thenReturn(Optional.of(dayWith(FROM, t)));

        service.rolloverForUser(USER, FROM, TO);

        verify(store, never()).getOrCreateDay(any(), any());
        verify(store, never()).saveDay(any());
    }

    @Test
    @DisplayName("DAILY recurring tasks are not rolled over - tomorrow's instance is pre-created")
    void rollover_dailyTask_skipped() {
        Task t = task("standup", 1, false, Recurrence.DAILY);
        when(store.findDay(USER, FROM)).thenReturn(Optional.of(dayWith(FROM, t)));

        service.rolloverForUser(USER, FROM, TO);

        verify(store, never()).getOrCreateDay(any(), any());
        verify(store, never()).saveDay(any());
    }

    @Test
    @DisplayName("tasks with a scheduledTime (alarm) are never rolled over")
    void rollover_taskWithAlarm_skipped() {
        Task t = task("meeting", 2, false, Recurrence.NONE);
        t.setScheduledTime(LocalTime.of(9, 30));
        when(store.findDay(USER, FROM)).thenReturn(Optional.of(dayWith(FROM, t)));

        service.rolloverForUser(USER, FROM, TO);

        verify(store, never()).getOrCreateDay(any(), any());
        verify(store, never()).saveDay(any());
    }

    @Test
    @DisplayName("weekly recurring tasks without an alarm are not rolled over - next week's instance is pre-created")
    void rollover_weeklyTaskWithoutAlarm_skipped() {
        Task t = task("weekly review", 5, false, Recurrence.WEEKLY);
        when(store.findDay(USER, FROM)).thenReturn(Optional.of(dayWith(FROM, t)));

        service.rolloverForUser(USER, FROM, TO);

        verify(store, never()).getOrCreateDay(any(), any());
        verify(store, never()).saveDay(any());
    }

    @Test
    @DisplayName("weekly recurring tasks WITH an alarm are not rolled over")
    void rollover_weeklyTaskWithAlarm_skipped() {
        Task t = task("weekly alarm", 5, false, Recurrence.WEEKLY);
        t.setScheduledTime(LocalTime.of(10, 0));
        when(store.findDay(USER, FROM)).thenReturn(Optional.of(dayWith(FROM, t)));

        service.rolloverForUser(USER, FROM, TO);

        verify(store, never()).getOrCreateDay(any(), any());
        verify(store, never()).saveDay(any());
    }

    @Test
    @DisplayName("monthly and yearly recurring tasks without an alarm are not rolled over - future instances are pre-created")
    void rollover_monthlyYearlyWithoutAlarm_skipped() {
        Task monthly = task("monthly report", 3, false, Recurrence.MONTHLY);
        Task yearly  = task("yearly review",  3, false, Recurrence.YEARLY);
        when(store.findDay(USER, FROM)).thenReturn(Optional.of(dayWith(FROM, monthly, yearly)));

        service.rolloverForUser(USER, FROM, TO);

        verify(store, never()).getOrCreateDay(any(), any());
        verify(store, never()).saveDay(any());
    }

    @Test
    @DisplayName("rolled-over tasks are appended after existing tasks on the target day")
    void rollover_appendsAfterExistingTasks() {
        Task incomplete = task("todo", 1, false, Recurrence.NONE);
        Day yesterday = dayWith(FROM, incomplete);

        Task existing = task("existing", 5, false, Recurrence.NONE);
        existing.setPosition(0);
        Day today = dayWith(TO, existing);

        when(store.findDay(USER, FROM)).thenReturn(Optional.of(yesterday));
        when(store.getOrCreateDay(USER, TO)).thenReturn(today);
        when(store.saveDay(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rolloverForUser(USER, FROM, TO);

        ArgumentCaptor<Day> saved = ArgumentCaptor.forClass(Day.class);
        verify(store).saveDay(saved.capture());
        List<Task> tasks = saved.getValue().getTasks();
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(1).getTitle()).isEqualTo("todo (moved)");
        assertThat(tasks.get(1).getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("no write when previous day has no tasks")
    void rollover_noPreviousDay_noWrite() {
        when(store.findDay(USER, FROM)).thenReturn(Optional.empty());

        service.rolloverForUser(USER, FROM, TO);

        verify(store, never()).saveDay(any());
    }

    @Test
    @DisplayName("rolloverIncompleteTasks iterates all users")
    void rolloverIncompleteTasks_allUsers() {
        User u1 = new User(); u1.setId("u1"); u1.setUsername("alice");
        User u2 = new User(); u2.setId("u2"); u2.setUsername("bob");
        when(store.listUsers()).thenReturn(List.of(u1, u2));
        when(store.findDay(any(), any())).thenReturn(Optional.empty());

        service.rolloverIncompleteTasks();

        verify(store).findDay(eq("u1"), any());
        verify(store).findDay(eq("u2"), any());
    }

    @Test
    @DisplayName("rolling over a task already titled '(moved)' produces '(moved x2)'")
    void movedTitle_alreadyMoved_incrementsToX2() {
        assertThat(TaskRolloverService.movedTitle("buy milk (moved)")).isEqualTo("buy milk (moved x2)");
    }

    @Test
    @DisplayName("rolling over a task already titled '(moved x2)' produces '(moved x3)'")
    void movedTitle_alreadyMovedX2_incrementsToX3() {
        assertThat(TaskRolloverService.movedTitle("buy milk (moved x2)")).isEqualTo("buy milk (moved x3)");
    }

    @Test
    @DisplayName("rolling over a plain task appends '(moved)'")
    void movedTitle_plain_appendsMoved() {
        assertThat(TaskRolloverService.movedTitle("buy milk")).isEqualTo("buy milk (moved)");
    }

    @Test
    @DisplayName("a crafted '(moved x99999999999)' title does not throw and is treated as plain")
    void movedTitle_oversizedCount_doesNotThrow() {
        assertThat(TaskRolloverService.movedTitle("pwn (moved x99999999999)"))
                .isEqualTo("pwn (moved x99999999999) (moved)");
    }

    @Test
    @DisplayName("rollover of a day containing a crafted '(moved x99999999999)' title still succeeds")
    void rollover_craftedMovedTitle_stillRollsOver() {
        Task crafted = task("pwn (moved x99999999999)", 1, false, Recurrence.NONE);
        Day yesterday = dayWith(FROM, crafted);
        Day today = new Day(USER, TO);

        when(store.findDay(USER, FROM)).thenReturn(Optional.of(yesterday));
        when(store.getOrCreateDay(USER, TO)).thenReturn(today);
        when(store.saveDay(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rolloverForUser(USER, FROM, TO);

        ArgumentCaptor<Day> saved = ArgumentCaptor.forClass(Day.class);
        verify(store).saveDay(saved.capture());
        assertThat(saved.getValue().getTasks().get(0).getTitle())
                .isEqualTo("pwn (moved x99999999999) (moved)");
    }

    @Test
    @DisplayName("a failure for one user does not stop rollover for the remaining users")
    void rolloverIncompleteTasks_oneUserFails_othersStillProcessed() {
        User u1 = new User(); u1.setId("u1"); u1.setUsername("alice");
        User u2 = new User(); u2.setId("u2"); u2.setUsername("bob");
        when(store.listUsers()).thenReturn(List.of(u1, u2));
        when(store.findDay(eq("u1"), any())).thenThrow(new RuntimeException("boom"));
        when(store.findDay(eq("u2"), any())).thenReturn(Optional.empty());

        service.rolloverIncompleteTasks();

        verify(store).findDay(eq("u1"), any());
        verify(store).findDay(eq("u2"), any());
    }

    // --- helpers ---

    private static Day dayWith(LocalDate date, Task... tasks) {
        Day day = new Day(USER, date);
        for (int i = 0; i < tasks.length; i++) {
            tasks[i].setPosition(i);
            day.getTasks().add(tasks[i]);
        }
        return day;
    }

    private static Task task(String title, int points, boolean completed, Recurrence recurrence) {
        Task t = new Task(title, points, 0);
        t.setId(java.util.UUID.randomUUID().toString());
        t.setCompleted(completed);
        t.setRecurrence(recurrence);
        return t;
    }
}
