package com.lilac.planner.service;

import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.model.Day;
import com.lilac.planner.model.Task;
import com.lilac.planner.persistence.PlannerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskRolloverService {

    // Digits bounded to 4 so a crafted title like "pwn (moved x99999999999)" can never
    // overflow Integer.parseInt; anything larger simply fails to match and gets " (moved)" appended.
    static final Pattern MOVED_PATTERN = Pattern.compile("^(.*) \\(moved(?: x(\\d{1,4}))?\\)$");

    private static final Logger log = LoggerFactory.getLogger(TaskRolloverService.class);

    private final PlannerStore store;

    public TaskRolloverService(PlannerStore store) {
        this.store = store;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void rolloverIncompleteTasks() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        log.info("Rolling over incomplete tasks from {} to {}", yesterday, today);
        // One user's bad data must never abort the loop for everyone after them.
        store.listUsers().forEach(user -> {
            try {
                rolloverForUser(user.getId(), yesterday, today);
            } catch (RuntimeException ex) {
                log.error("Rollover failed for user {}; continuing with remaining users", user.getId(), ex);
            }
        });
    }

    public void rolloverForUser(String userId, LocalDate from, LocalDate to) {
        store.findDay(userId, from).ifPresent(fromDay -> {
            List<Task> incomplete = fromDay.getTasks().stream()
                    // Tasks with a scheduledTime (alarm) are time-specific: they are not
                    // rolled over because the alarm no longer applies on a different day.
                    // All recurring tasks (DAILY, WEEKLY, MONTHLY, YEARLY) already have
                    // future instances pre-created by PlannerService.addTask; rolling them
                    // over would produce an orphaned NONE clone alongside the real series.
                    .filter(t -> !t.isCompleted()
                            && t.getScheduledTime() == null
                            && t.getRecurrence() == Recurrence.NONE)
                    .toList();
            if (incomplete.isEmpty()) return;

            Day toDay = store.getOrCreateDay(userId, to);
            int startPosition = toDay.getTasks().size();
            for (int i = 0; i < incomplete.size(); i++) {
                Task src = incomplete.get(i);
                Task copy = new Task(movedTitle(src.getTitle()), src.getPoints(), startPosition + i);
                // Rolled-over copies are always one-off reminders, not part of the series.
                copy.setRecurrence(Recurrence.NONE);
                toDay.getTasks().add(copy);
            }
            store.saveDay(toDay);
            log.debug("Rolled over {} incomplete task(s) for user {} from {} to {}",
                    incomplete.size(), userId, from, to);
        });
    }

    public static String movedTitle(String title) {
        Matcher m = MOVED_PATTERN.matcher(title);
        if (m.matches()) {
            try {
                int count = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
                return m.group(1) + " (moved x" + (count + 1) + ")";
            } catch (NumberFormatException ignored) {
                // Defence in depth: the pattern caps the digits, but a parse failure
                // must never abort the rollover - fall through and append instead.
            }
        }
        return title + " (moved)";
    }
}
