package com.lilac.planner.unit;

import com.lilac.planner.domain.Platform;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.ExpoPushSender;
import com.lilac.planner.service.PushAlarmDispatchService;
import com.lilac.planner.service.PushPayload;
import com.lilac.planner.service.PushSendResult;
import com.lilac.planner.service.WebPushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushAlarmDispatchService - per-user alarm push dispatch")
class PushAlarmDispatchServiceUnitTest {

    // Pinned instant so window/day-boundary math never races the real wall clock.
    private static final Instant NOW_INSTANT = Instant.parse("2026-07-10T12:00:00Z");
    private static final ZonedDateTime NOW_UTC = ZonedDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock PlannerStore store;
    @Mock ExpoPushSender expoPushSender;
    @Mock WebPushSender webPushSender;

    private PushAlarmDispatchService service;

    @BeforeEach
    void setUp() {
        service = new PushAlarmDispatchService(store, expoPushSender, webPushSender);
        ReflectionTestUtils.setField(service, "defaultTimezone", "UTC");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("a task whose alarm window is open dispatches exactly one push per subscription")
    void windowOpen_dispatchesOncePerSubscription() {
        Task task = task("water plants", 2, NOW_UTC.toLocalTime());
        Day day = dayWith(NOW_UTC.toLocalDate(), task);
        User user = user("u1", null);

        PushSubscription expo = subscription("u1", Platform.EXPO, "sub-expo");
        PushSubscription web = subscription("u1", Platform.WEB, "sub-web");

        when(store.listUsers()).thenReturn(List.of(user));
        when(store.listPushSubscriptions("u1")).thenReturn(List.of(expo, web));
        when(store.findDay("u1", NOW_UTC.toLocalDate())).thenReturn(Optional.of(day));
        when(store.markAlarmDispatched("u1", NOW_UTC.toLocalDate(), task.getId())).thenReturn(true);
        when(expoPushSender.send(eq(expo), any())).thenReturn(PushSendResult.success());
        when(webPushSender.send(eq(web), any())).thenReturn(PushSendResult.success());

        service.dispatchDueAlarms();

        ArgumentCaptor<PushPayload> expoPayload = ArgumentCaptor.forClass(PushPayload.class);
        ArgumentCaptor<PushPayload> webPayload = ArgumentCaptor.forClass(PushPayload.class);
        verify(expoPushSender, times(1)).send(eq(expo), expoPayload.capture());
        verify(webPushSender, times(1)).send(eq(web), webPayload.capture());
        assertThat(expoPayload.getValue().title()).isEqualTo("🌸 Lilac Planner reminder");
        assertThat(expoPayload.getValue().body()).isEqualTo("water plants (2 pts)");
        assertThat(webPayload.getValue().body()).isEqualTo("water plants (2 pts)");
    }

    @Test
    @DisplayName("a task whose alarm window is closed does not dispatch")
    void windowClosed_doesNotDispatch() {
        Task task = task("far away", 1, NOW_UTC.plusHours(3).toLocalTime());
        Day day = dayWith(NOW_UTC.toLocalDate(), task);
        User user = user("u1", null);

        when(store.listUsers()).thenReturn(List.of(user));
        when(store.listPushSubscriptions("u1")).thenReturn(List.of(subscription("u1", Platform.EXPO, "sub-expo")));
        when(store.findDay("u1", NOW_UTC.toLocalDate())).thenReturn(Optional.of(day));

        service.dispatchDueAlarms();

        verify(store, never()).markAlarmDispatched(any(), any(), any());
        verify(expoPushSender, never()).send(any(), any());
        verify(webPushSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("dedup prevents a resend on a second tick for the same task")
    void dedup_preventsResendOnSecondTick() {
        Task task = task("standup", 1, NOW_UTC.toLocalTime());
        Day day = dayWith(NOW_UTC.toLocalDate(), task);
        User user = user("u1", null);
        PushSubscription expo = subscription("u1", Platform.EXPO, "sub-expo");

        when(store.listUsers()).thenReturn(List.of(user));
        when(store.listPushSubscriptions("u1")).thenReturn(List.of(expo));
        when(store.findDay("u1", NOW_UTC.toLocalDate())).thenReturn(Optional.of(day));
        when(store.markAlarmDispatched("u1", NOW_UTC.toLocalDate(), task.getId())).thenReturn(true, false);
        when(expoPushSender.send(eq(expo), any())).thenReturn(PushSendResult.success());

        service.dispatchDueAlarms();
        service.dispatchDueAlarms();

        verify(store, times(2)).markAlarmDispatched("u1", NOW_UTC.toLocalDate(), task.getId());
        verify(expoPushSender, times(1)).send(eq(expo), any());
    }

    @Test
    @DisplayName("an invalid subscription reported by a sender is pruned")
    void invalidSubscription_isPruned() {
        Task task = task("expired device", 1, NOW_UTC.toLocalTime());
        Day day = dayWith(NOW_UTC.toLocalDate(), task);
        User user = user("u1", null);
        PushSubscription web = subscription("u1", Platform.WEB, "sub-web");

        when(store.listUsers()).thenReturn(List.of(user));
        when(store.listPushSubscriptions("u1")).thenReturn(List.of(web));
        when(store.findDay("u1", NOW_UTC.toLocalDate())).thenReturn(Optional.of(day));
        when(store.markAlarmDispatched("u1", NOW_UTC.toLocalDate(), task.getId())).thenReturn(true);
        when(webPushSender.send(eq(web), any())).thenReturn(PushSendResult.invalidSubscription());

        service.dispatchDueAlarms();

        verify(store).deletePushSubscription("u1", web.getId());
    }

    @Test
    @DisplayName("per-user timezone determines which calendar date is treated as 'today'")
    void perUserTimezone_determinesTodayForDayLookup() {
        User aheadOfUtc = user("u1", "Pacific/Kiritimati"); // UTC+14
        User behindUtc = user("u2", "Etc/GMT+12");          // UTC-12; Etc/GMT signs are POSIX-inverted
        PushSubscription sub1 = subscription("u1", Platform.EXPO, "sub-1");
        PushSubscription sub2 = subscription("u2", Platform.EXPO, "sub-2");

        when(store.listUsers()).thenReturn(List.of(aheadOfUtc, behindUtc));
        when(store.listPushSubscriptions("u1")).thenReturn(List.of(sub1));
        when(store.listPushSubscriptions("u2")).thenReturn(List.of(sub2));
        when(store.findDay(any(), any())).thenReturn(Optional.empty());

        service.dispatchDueAlarms();

        LocalDate expectedForUser1 = ZonedDateTime.ofInstant(NOW_INSTANT, ZoneId.of("Pacific/Kiritimati")).toLocalDate();
        LocalDate expectedForUser2 = ZonedDateTime.ofInstant(NOW_INSTANT, ZoneId.of("Etc/GMT+12")).toLocalDate();
        assertThat(expectedForUser1).isNotEqualTo(expectedForUser2);
        verify(store).findDay("u1", expectedForUser1);
        verify(store).findDay("u2", expectedForUser2);
    }

    @Test
    @DisplayName("an unparseable user timezone falls back to the configured default")
    void invalidUserTimezone_fallsBackToDefault() {
        ReflectionTestUtils.setField(service, "defaultTimezone", "Europe/Prague");

        assertThat(service.resolveZone("Not/A_Real_Zone")).isEqualTo(ZoneId.of("Europe/Prague"));
    }

    @Test
    @DisplayName("an unparseable default timezone falls back to UTC")
    void invalidDefaultTimezone_fallsBackToUtc() {
        ReflectionTestUtils.setField(service, "defaultTimezone", "Not/A_Real_Zone");

        assertThat(service.resolveZone(null)).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("a task from just before midnight is still caught by the tick just after midnight")
    void midnightBoundary_lateTickStillCatchesYesterdaysTask() {
        Instant justAfterMidnight = Instant.parse("2026-07-11T00:02:00Z");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(justAfterMidnight, ZoneOffset.UTC));

        LocalDate yesterday = LocalDate.of(2026, 7, 10);
        LocalDate today = LocalDate.of(2026, 7, 11);
        Task task = task("late reminder", 1, LocalTime.of(23, 59));
        Day yesterdayDay = dayWith(yesterday, task);
        User user = user("u1", null);
        PushSubscription expo = subscription("u1", Platform.EXPO, "sub-expo");

        when(store.listUsers()).thenReturn(List.of(user));
        when(store.listPushSubscriptions("u1")).thenReturn(List.of(expo));
        when(store.findDay("u1", today)).thenReturn(Optional.empty());
        when(store.findDay("u1", yesterday)).thenReturn(Optional.of(yesterdayDay));
        when(store.markAlarmDispatched("u1", yesterday, task.getId())).thenReturn(true);
        when(expoPushSender.send(eq(expo), any())).thenReturn(PushSendResult.success());

        service.dispatchDueAlarms();

        verify(store).markAlarmDispatched("u1", yesterday, task.getId());
        verify(expoPushSender, times(1)).send(eq(expo), any());
    }

    @Test
    @DisplayName("once well past midnight, yesterday's day is no longer re-checked")
    void midnightBoundary_wellPastMidnight_doesNotRecheckYesterday() {
        Instant wellPastMidnight = Instant.parse("2026-07-11T00:10:00Z");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(wellPastMidnight, ZoneOffset.UTC));

        LocalDate today = LocalDate.of(2026, 7, 11);
        LocalDate yesterday = LocalDate.of(2026, 7, 10);
        User user = user("u1", null);

        when(store.listUsers()).thenReturn(List.of(user));
        when(store.listPushSubscriptions("u1")).thenReturn(List.of(subscription("u1", Platform.EXPO, "sub-expo")));
        when(store.findDay("u1", today)).thenReturn(Optional.empty());

        service.dispatchDueAlarms();

        verify(store, never()).findDay("u1", yesterday);
    }

    @Test
    @DisplayName("isWindowOpen: fires exactly at the 30s-early edge, not 1s beyond it")
    void isWindowOpen_earlyEdge() {
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime scheduledTime = LocalTime.of(12, 0);

        assertThat(PushAlarmDispatchService.isWindowOpen(date, scheduledTime, NOW_UTC.minusSeconds(30))).isTrue();
        assertThat(PushAlarmDispatchService.isWindowOpen(date, scheduledTime, NOW_UTC.minusSeconds(31))).isFalse();
    }

    @Test
    @DisplayName("isWindowOpen: fires exactly at the 5-minutes-late edge, not 1s beyond it")
    void isWindowOpen_lateEdge() {
        LocalDate date = LocalDate.of(2026, 7, 10);
        LocalTime scheduledTime = LocalTime.of(12, 0);

        assertThat(PushAlarmDispatchService.isWindowOpen(date, scheduledTime, NOW_UTC.plusMinutes(5))).isTrue();
        assertThat(PushAlarmDispatchService.isWindowOpen(date, scheduledTime, NOW_UTC.plusMinutes(5).plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("a failure for one user does not stop dispatch for the remaining users")
    void oneUserFails_othersStillProcessed() {
        User u1 = user("u1", null);
        User u2 = user("u2", null);
        when(store.listUsers()).thenReturn(List.of(u1, u2));
        when(store.listPushSubscriptions("u1")).thenThrow(new RuntimeException("boom"));
        when(store.listPushSubscriptions("u2")).thenReturn(List.of());

        service.dispatchDueAlarms();

        verify(store).listPushSubscriptions("u1");
        verify(store).listPushSubscriptions("u2");
    }

    // --- helpers ---

    private static User user(String id, String timezone) {
        User u = new User();
        u.setId(id);
        u.setUsername(id);
        u.setTimezone(timezone);
        return u;
    }

    private static PushSubscription subscription(String userId, Platform platform, String id) {
        PushSubscription s = new PushSubscription(userId, platform, "token-" + id);
        s.setId(id);
        return s;
    }

    private static Task task(String title, int points, LocalTime scheduledTime) {
        Task t = new Task(title, points, 0);
        t.setId(UUID.randomUUID().toString());
        t.setScheduledTime(scheduledTime.withSecond(0).withNano(0));
        return t;
    }

    private static Day dayWith(LocalDate date, Task... tasks) {
        Day day = new Day("ignored", date);
        for (Task t : tasks) {
            day.getTasks().add(t);
        }
        return day;
    }
}
