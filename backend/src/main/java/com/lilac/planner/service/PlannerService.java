package com.lilac.planner.service;

import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.dto.TaskRequest;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.model.Task;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.util.Uuids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class PlannerService {

    private static final String DAY_NOT_FOUND = "Day not found: ";

    private static final Map<Recurrence, Integer> RECURRENCE_HORIZON = Map.of(
            Recurrence.DAILY,   30,
            Recurrence.WEEKLY,  12,
            Recurrence.MONTHLY, 12,
            Recurrence.YEARLY,   5
    );

    private final PlannerStore store;
    private final StickerCatalog catalog;

    @Value("${planner.stickers.base-threshold:20}")
    private int baseThreshold;

    @Value("${planner.stickers.threshold-step:10}")
    private int thresholdStep;

    public PlannerService(PlannerStore store, StickerCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    public Day getOrCreateDay(String userId, LocalDate date) {
        return store.getOrCreateDay(userId, date);
    }

    @Transactional
    public Day addTask(String userId, LocalDate date, TaskRequest req) {
        Recurrence recurrence = req.recurrence() == null ? Recurrence.NONE : req.recurrence();
        String groupId = recurrence == Recurrence.NONE ? null : Uuids.v7();

        Day day = addSingleInstance(userId, date, req, recurrence, groupId);
        if (recurrence != Recurrence.NONE) {
            for (LocalDate future : futureDates(date, recurrence)) {
                addSingleInstance(userId, future, req, recurrence, groupId);
            }
        }
        return store.findDay(userId, date).orElse(day);
    }

    private Day addSingleInstance(String userId, LocalDate date, TaskRequest req,
                                  Recurrence recurrence, String groupId) {
        Day day = store.getOrCreateDay(userId, date);
        Task t = new Task(
                req.title(),
                req.points() == null ? 1 : Math.max(0, req.points()),
                req.position() == null ? day.getTasks().size() : req.position()
        );
        t.setScheduledTime(req.scheduledTime());
        t.setRecurrence(recurrence);
        t.setRecurrenceGroupId(groupId);
        day.getTasks().add(t);
        awardStickersIfNeeded(day);
        return store.saveDay(day);
    }

    private List<LocalDate> futureDates(LocalDate start, Recurrence rec) {
        int horizon = RECURRENCE_HORIZON.getOrDefault(rec, 0);
        List<LocalDate> dates = new ArrayList<>(horizon);
        for (int i = 1; i <= horizon; i++) {
            dates.add(switch (rec) {
                case DAILY   -> start.plusDays(i);
                case WEEKLY  -> start.plusWeeks(i);
                case MONTHLY -> start.plusMonths(i);
                case YEARLY  -> start.plusYears(i);
                case NONE    -> start;
            });
        }
        return dates;
    }

    @Transactional
    public Day updateTask(String userId, LocalDate date, String taskId, TaskRequest req) {
        Day day = store.findDay(userId, date)
                .orElseThrow(() -> new NoSuchElementException(DAY_NOT_FOUND + date));
        Task target = day.getTasks().stream()
                .filter(t -> Objects.equals(t.getId(), taskId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
        boolean titleChanged = req.title() != null && !req.title().isBlank();
        Integer validatedPoints = req.points() != null ? Math.max(0, req.points()) : null;
        if (titleChanged) target.setTitle(req.title());
        if (validatedPoints != null) target.setPoints(validatedPoints);
        if (req.completed() != null) target.setCompleted(req.completed());
        if (req.position() != null) target.setPosition(req.position());
        if (Boolean.TRUE.equals(req.clearScheduledTime())) target.setScheduledTime(null);
        else if (req.scheduledTime() != null) target.setScheduledTime(req.scheduledTime());
        awardStickersIfNeeded(day);
        Day saved = store.saveDay(day);
        if ((titleChanged || validatedPoints != null) && target.getRecurrenceGroupId() != null) {
            store.updateRecurringTaskSeriesFields(userId, target.getRecurrenceGroupId(),
                    titleChanged ? req.title() : null, validatedPoints);
        }
        return store.findDay(userId, date).orElse(saved);
    }

    @Transactional
    public Day deleteTask(String userId, LocalDate date, String taskId) {
        Task task = store.findDay(userId, date)
                .flatMap(d -> d.getTasks().stream().filter(t -> Objects.equals(t.getId(), taskId)).findFirst())
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));

        store.deleteTask(userId, date, taskId);

        String groupId = task.getRecurrenceGroupId();
        if (groupId != null) {
            store.deleteTasksByRecurrenceGroup(userId, date, groupId);
        }

        Day day = store.findDay(userId, date)
                .orElseThrow(() -> new NoSuchElementException(DAY_NOT_FOUND + date));
        awardStickersIfNeeded(day);
        return store.saveDay(day);
    }

    @Transactional
    public Day reorderTasks(String userId, LocalDate date, List<String> orderedIds) {
        Day day = store.findDay(userId, date)
                .orElseThrow(() -> new NoSuchElementException(DAY_NOT_FOUND + date));
        Map<String, Integer> rankById = new HashMap<>();
        for (int i = 0; i < orderedIds.size(); i++) rankById.put(orderedIds.get(i), i);
        int tail = orderedIds.size();
        for (Task t : day.getTasks()) {
            Integer rank = rankById.get(t.getId());
            t.setPosition(rank == null ? tail++ : rank);
        }
        return store.saveDay(day);
    }

    private void awardStickersIfNeeded(Day day) {
        int points = day.totalPoints();
        int eligible = points < baseThreshold ? 0
                : 1 + (points - baseThreshold) / Math.max(1, thresholdStep);
        if (day.getEarnedStickers().size() > eligible) {
            day.getEarnedStickers().subList(eligible, day.getEarnedStickers().size()).clear();
        }
        while (day.getEarnedStickers().size() < eligible) {
            long seed = day.getDate().toEpochDay() * 1_000_003L
                    + day.getEarnedStickers().size() * 31L;
            List<Sticker> remaining = catalog.all().stream()
                    .filter(s -> !day.getEarnedStickers().contains(s.code()))
                    .toList();
            if (remaining.isEmpty()) break;
            day.getEarnedStickers().add(remaining.get(Math.floorMod(seed, remaining.size())).code());
        }
    }

    public int nextThreshold(int points) {
        if (points < baseThreshold) return baseThreshold;
        int reachedIdx = (points - baseThreshold) / thresholdStep;
        return baseThreshold + (reachedIdx + 1) * thresholdStep;
    }

    public PushSubscription registerPushSubscription(PushSubscription subscription) {
        return store.savePushSubscription(subscription);
    }

    public List<PushSubscription> listPushSubscriptions(String userId) {
        return store.listPushSubscriptions(userId);
    }

    public boolean deletePushSubscription(String userId, String subscriptionId) {
        return store.deletePushSubscription(userId, subscriptionId);
    }

    public void updateUserTimezone(String userId, String timezone) {
        store.updateUserTimezone(userId, timezone);
    }
}
