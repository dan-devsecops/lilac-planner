package com.lilac.planner.unit;

import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.dto.TaskRequest;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.Task;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.PlannerService;
import com.lilac.planner.service.Sticker;
import com.lilac.planner.service.StickerCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlannerService - business logic")
class PlannerServiceUnitTest {

    @Mock PlannerStore store;
    @Mock StickerCatalog catalog;
    @InjectMocks PlannerService service;

    static final LocalDate DATE = LocalDate.of(2099, 6, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseThreshold", 20);
        ReflectionTestUtils.setField(service, "thresholdStep", 10);
        when(catalog.all()).thenReturn(List.of(new Sticker("kitty", "🐱", "Kitty")));
        when(catalog.pickFor(anyLong())).thenReturn(new Sticker("kitty", "🐱", "Kitty"));
    }

    // --- addTask ---

    @Test
    @DisplayName("null points defaults to 1")
    void addTask_nullPoints_defaultsToOne() {
        Day day = dayFor(DATE);
        when(store.getOrCreateDay("u1", DATE)).thenReturn(day);
        when(store.saveDay(any())).thenReturn(day);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));

        service.addTask("u1", DATE, new TaskRequest("read", null, null, null, null, null, null));

        ArgumentCaptor<Day> captor = ArgumentCaptor.forClass(Day.class);
        verify(store, atLeastOnce()).saveDay(captor.capture());
        assertThat(captor.getAllValues().get(0).getTasks().get(0).getPoints()).isEqualTo(1);
    }

    @Test
    @DisplayName("negative points are clamped to zero")
    void addTask_negativePoints_clampsToZero() {
        Day day = dayFor(DATE);
        when(store.getOrCreateDay("u1", DATE)).thenReturn(day);
        when(store.saveDay(any())).thenReturn(day);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));

        service.addTask("u1", DATE, new TaskRequest("read", -5, null, null, null, null, null));

        ArgumentCaptor<Day> captor = ArgumentCaptor.forClass(Day.class);
        verify(store, atLeastOnce()).saveDay(captor.capture());
        assertThat(captor.getAllValues().get(0).getTasks().get(0).getPoints()).isZero();
    }

    @Test
    @DisplayName("DAILY recurrence creates one instance per day for 30 days (31 total)")
    void addTask_dailyRecurrence_creates31DaySlots() {
        Day day = dayFor(DATE);
        when(store.getOrCreateDay(eq("u1"), any(LocalDate.class))).thenReturn(day);
        when(store.saveDay(any())).thenReturn(day);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));

        service.addTask("u1", DATE, new TaskRequest("standup", 1, null, null, null, null, Recurrence.DAILY));

        verify(store, times(31)).getOrCreateDay(eq("u1"), any(LocalDate.class));
    }

    @Test
    @DisplayName("WEEKLY recurrence creates 12 future weeks (13 total)")
    void addTask_weeklyRecurrence_creates13WeekSlots() {
        Day day = dayFor(DATE);
        when(store.getOrCreateDay(eq("u1"), any(LocalDate.class))).thenReturn(day);
        when(store.saveDay(any())).thenReturn(day);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));

        service.addTask("u1", DATE, new TaskRequest("standup", 1, null, null, null, null, Recurrence.WEEKLY));

        verify(store, times(13)).getOrCreateDay(eq("u1"), any(LocalDate.class));
    }

    @Test
    @DisplayName("all recurring instances share the same recurrenceGroupId")
    void addTask_recurringInstances_shareGroupId() {
        Day day = dayFor(DATE);
        when(store.getOrCreateDay(eq("u1"), any(LocalDate.class))).thenReturn(day);
        when(store.saveDay(any())).thenReturn(day);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));

        service.addTask("u1", DATE, new TaskRequest("yoga", 1, null, null, null, null, Recurrence.DAILY));

        ArgumentCaptor<Day> captor = ArgumentCaptor.forClass(Day.class);
        verify(store, atLeastOnce()).saveDay(captor.capture());

        List<String> groupIds = captor.getAllValues().stream()
                .flatMap(d -> d.getTasks().stream())
                .map(Task::getRecurrenceGroupId)
                .distinct()
                .toList();
        assertThat(groupIds).hasSize(1).doesNotContainNull();
    }

    // --- updateTask ---

    @Test
    @DisplayName("updateTask throws NoSuchElementException when day does not exist")
    void updateTask_dayNotFound_throws() {
        when(store.findDay("u1", DATE)).thenReturn(Optional.empty());

        TaskRequest req = new TaskRequest(null, null, true, null, null, null, null);
        assertThatThrownBy(() -> service.updateTask("u1", DATE, "1", req))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(DATE.toString());
    }

    @Test
    @DisplayName("updateTask throws NoSuchElementException when task id is not in the day")
    void updateTask_taskNotFound_throws() {
        Day day = dayFor(DATE);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));

        TaskRequest req = new TaskRequest(null, null, true, null, null, null, null);
        assertThatThrownBy(() -> service.updateTask("u1", DATE, "999", req))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("partial update only changes explicitly provided fields")
    void updateTask_partial_keepsOtherFieldsUnchanged() {
        Task task = task("original", 5);
        Day day = dayFor(DATE);
        day.getTasks().add(task);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));
        when(store.saveDay(any())).thenReturn(day);

        service.updateTask("u1", DATE, task.getId(), new TaskRequest(null, null, true, null, null, null, null));

        assertThat(task.getTitle()).isEqualTo("original");
        assertThat(task.getPoints()).isEqualTo(5);
        assertThat(task.isCompleted()).isTrue();
    }

    // --- deleteTask ---

    @Test
    @DisplayName("deleteTask throws NoSuchElementException when task is absent")
    void deleteTask_taskAbsent_throws() {
        Day day = dayFor(DATE);
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));

        assertThatThrownBy(() -> service.deleteTask("u1", DATE, "999"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("stickers are removed when a completed task is unchecked and points fall below threshold")
    void updateTask_uncomplete_removesExcessStickers() {
        Task task = task("run", 20);
        task.setCompleted(true);
        Day day = dayFor(DATE);
        day.getTasks().add(task);
        day.getEarnedStickers().add("kitty");
        when(store.findDay("u1", DATE)).thenReturn(Optional.of(day));
        when(store.saveDay(any())).thenReturn(day);

        service.updateTask("u1", DATE, task.getId(), new TaskRequest(null, null, false, null, null, null, null));

        assertThat(day.getEarnedStickers()).isEmpty();
    }

    @Test
    @DisplayName("stickers are removed when a completed task is deleted and points fall below threshold")
    void deleteTask_completedTask_removesExcessStickers() {
        Task task = task("run", 20);
        task.setCompleted(true);
        Day dayBefore = dayFor(DATE);
        dayBefore.getTasks().add(task);
        dayBefore.getEarnedStickers().add("kitty");
        Day dayAfter = dayFor(DATE);

        when(store.findDay("u1", DATE)).thenReturn(Optional.of(dayBefore), Optional.of(dayAfter));

        service.deleteTask("u1", DATE, task.getId());

        assertThat(dayAfter.getEarnedStickers()).isEmpty();
    }

    @Test
    @DisplayName("deleting a recurring task also removes it from all future days")
    void deleteTask_recurring_deletesFutureInstances() {
        String groupId = "grp-1";
        Task task = task("run", 5);
        task.setRecurrenceGroupId(groupId);
        task.setRecurrence(Recurrence.DAILY);
        Day currentDay = dayFor(DATE);
        currentDay.getTasks().add(task);

        when(store.findDay("u1", DATE)).thenReturn(Optional.of(currentDay), Optional.of(dayFor(DATE)));

        service.deleteTask("u1", DATE, task.getId());

        verify(store).deleteTask("u1", DATE, task.getId());
        verify(store).deleteTasksByRecurrenceGroup("u1", DATE, groupId);
    }

    // --- nextThreshold ---

    @Test
    @DisplayName("nextThreshold returns the base when points are below it")
    void nextThreshold_belowBase_returnsBase() {
        assertThat(service.nextThreshold(0)).isEqualTo(20);
        assertThat(service.nextThreshold(19)).isEqualTo(20);
    }

    @Test
    @DisplayName("nextThreshold advances by step when points are at or above the base")
    void nextThreshold_atOrAboveBase_advancesByStep() {
        assertThat(service.nextThreshold(20)).isEqualTo(30);
        assertThat(service.nextThreshold(29)).isEqualTo(30);
        assertThat(service.nextThreshold(30)).isEqualTo(40);
    }

    // --- helpers ---

    private static Day dayFor(LocalDate date) {
        return new Day("u1", date);
    }

    private static Task task(String title, int points) {
        Task t = new Task(title, points, 0);
        t.setId("1");
        return t;
    }
}
