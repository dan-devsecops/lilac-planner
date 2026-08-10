package com.lilac.planner.service;

import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Polls every user's "today" (in their own timezone) for tasks whose alarm window has just
 * opened and pushes a notification to every registered device, mirroring the client-side
 * {@code shouldFireNow} window in {@code frontend/src/notifications.js} (fires from 30s before
 * the scheduled minute up to 5 minutes after). Deliberately a separate bean from
 * {@link TaskRolloverService} - that one runs once a day at midnight server time; this one
 * runs every few seconds and reasons about time per-user.
 */
@Service
public class PushAlarmDispatchService {

    private static final Duration FIRE_WINDOW_BEFORE = Duration.ofSeconds(30);
    private static final Duration FIRE_WINDOW_AFTER = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(PushAlarmDispatchService.class);

    private final PlannerStore store;
    private final ExpoPushSender expoPushSender;
    private final WebPushSender webPushSender;

    // Overridable in tests via ReflectionTestUtils so window/day-boundary math can be pinned to
    // an exact instant instead of racing the real wall clock.
    private Clock clock = Clock.systemUTC();

    @Value("${planner.push.default-timezone:UTC}")
    private String defaultTimezone;

    public PushAlarmDispatchService(PlannerStore store, ExpoPushSender expoPushSender, WebPushSender webPushSender) {
        this.store = store;
        this.expoPushSender = expoPushSender;
        this.webPushSender = webPushSender;
    }

    @Scheduled(fixedRateString = "${planner.push.alarm-poll-interval:PT30S}")
    public void dispatchDueAlarms() {
        // One user's bad data (or a sender outage) must never abort the tick for everyone after them.
        store.listUsers().forEach(user -> {
            try {
                dispatchForUser(user);
            } catch (RuntimeException ex) {
                log.error("Push alarm dispatch failed for user {}; continuing with remaining users", user.getId(), ex);
            }
        });
    }

    private void dispatchForUser(User user) {
        List<PushSubscription> subscriptions = store.listPushSubscriptions(user.getId());
        if (subscriptions.isEmpty()) {
            return;
        }

        ZoneId zone = resolveZone(user.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        LocalDate today = now.toLocalDate();

        dispatchForDate(user, today, now, subscriptions);

        // The fire window extends up to 5 minutes past the scheduled minute. If "now" is still
        // within that window of midnight, a task scheduled in the last few minutes of yesterday
        // may still be open but yesterday is no longer "today" - an earlier tick that should have
        // caught it (server restart, GC pause, a transient store failure) would otherwise mean the
        // alarm is silently and permanently missed, so yesterday's day gets a narrow re-check here
        // too - markAlarmDispatched's dedup makes repeating this safe every tick.
        if (now.toLocalTime().isBefore(LocalTime.MIDNIGHT.plus(FIRE_WINDOW_AFTER))) {
            dispatchForDate(user, today.minusDays(1), now, subscriptions);
        }
    }

    private void dispatchForDate(User user, LocalDate date, ZonedDateTime now, List<PushSubscription> subscriptions) {
        store.findDay(user.getId(), date).ifPresent(day ->
                day.getTasks().forEach(task -> {
                    try {
                        dispatchForTask(user, day, task, now, subscriptions);
                    } catch (RuntimeException ex) {
                        log.error("Push alarm dispatch failed for user {} task {}; continuing",
                                user.getId(), task.getId(), ex);
                    }
                }));
    }

    private void dispatchForTask(User user, Day day, Task task, ZonedDateTime now, List<PushSubscription> subscriptions) {
        if (task.isCompleted() || task.getScheduledTime() == null
                || !isWindowOpen(day.getDate(), task.getScheduledTime(), now)) {
            return;
        }

        // Insert-if-absent: only the tick that actually wins the dedup marker sends pushes,
        // so re-running this same tick logic (or a slow/duplicate tick) never double-sends.
        if (!store.markAlarmDispatched(user.getId(), day.getDate(), task.getId())) {
            return;
        }

        PushPayload payload = new PushPayload(
                "🌸 Lilac Planner reminder",
                task.getTitle() + " (" + task.getPoints() + " pts)");
        for (PushSubscription subscription : subscriptions) {
            sendAndPruneIfInvalid(subscription, payload);
        }
    }

    private void sendAndPruneIfInvalid(PushSubscription subscription, PushPayload payload) {
        PushSendResult result = switch (subscription.getPlatform()) {
            case EXPO -> expoPushSender.send(subscription, payload);
            case WEB -> webPushSender.send(subscription, payload);
        };
        if (result.isInvalidSubscription()) {
            store.deletePushSubscription(subscription.getUserId(), subscription.getId());
        }
    }

    /**
     * @param date the calendar date {@code scheduledTime} belongs to (the Day it was fetched
     *             from) - kept distinct from {@code now}'s date so a task from yesterday can be
     *             checked correctly after midnight without appearing to be ~24h in the future.
     */
    public static boolean isWindowOpen(LocalDate date, LocalTime scheduledTime, ZonedDateTime now) {
        ZonedDateTime target = ZonedDateTime.of(date, scheduledTime, now.getZone());
        Duration delta = Duration.between(target, now);
        return delta.compareTo(FIRE_WINDOW_BEFORE.negated()) >= 0 && delta.compareTo(FIRE_WINDOW_AFTER) <= 0;
    }

    public ZoneId resolveZone(String timezone) {
        String candidate = (timezone == null || timezone.isBlank()) ? defaultTimezone : timezone;
        try {
            return ZoneId.of(candidate);
        } catch (DateTimeException ex) {
            if (!candidate.equals(defaultTimezone)) {
                log.warn("Invalid user timezone '{}'; falling back to default '{}'", candidate, defaultTimezone);
                try {
                    return ZoneId.of(defaultTimezone);
                } catch (DateTimeException ex2) {
                    log.warn("Invalid default timezone '{}'; falling back to UTC", defaultTimezone);
                    return ZoneId.of("UTC");
                }
            }
            log.warn("Invalid default timezone '{}'; falling back to UTC", candidate);
            return ZoneId.of("UTC");
        }
    }
}
