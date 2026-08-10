package com.lilac.planner.service;

import com.lilac.planner.persistence.PlannerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Periodic purge of stale {@code AlarmDispatch} dedup rows, mirroring
 * {@link AuthTokenCleanupService}'s pattern for auth tokens. Without this, every alarm ever
 * fired would leave a permanent (userId, date, taskId) row behind.
 *
 * <p>{@link PushAlarmDispatchService} only ever looks up "today" and, near midnight,
 * "yesterday" - both computed in each user's own timezone. A dispatch record more than a
 * couple of days old can therefore never be looked up again; the default retention window
 * (7 days) leaves a wide safety margin over the worst case (IANA zones span UTC-12..UTC+14,
 * so a record dated {@code D} can be reached at the very latest ~1.5 days after {@code D} -
 * when a UTC-12 user's "yesterday" re-check fires just past their local midnight). Keep
 * {@code planner.push.alarm-dispatch-retention-days} at 2 or more; below that the safety
 * margin over the worst case narrows to nothing.</p>
 */
@Service
public class AlarmDispatchCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AlarmDispatchCleanupService.class);

    private final PlannerStore store;

    @Value("${planner.push.alarm-dispatch-retention-days:7}")
    private long retentionDays;

    // Overridable in tests via ReflectionTestUtils, mirroring PushAlarmDispatchService's clock seam.
    private Clock clock = Clock.systemUTC();

    public AlarmDispatchCleanupService(PlannerStore store) {
        this.store = store;
    }

    @Scheduled(initialDelayString = "PT15M", fixedRateString = "PT6H")
    public void purgeExpiredDispatches() {
        LocalDate cutoff = LocalDate.now(clock).minusDays(retentionDays);
        int purged = store.deleteExpiredAlarmDispatches(cutoff);
        if (purged > 0) {
            log.info("Purged {} expired alarm dispatch record(s)", purged);
        } else {
            log.debug("No expired alarm dispatch records to purge");
        }
    }
}
