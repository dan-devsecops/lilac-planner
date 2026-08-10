package com.lilac.planner.unit;

import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.AlarmDispatchCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmDispatchCleanupService - periodic stale-dispatch-row purge")
class AlarmDispatchCleanupServiceUnitTest {

    private static final Instant NOW_INSTANT = Instant.parse("2099-06-15T00:00:00Z");

    @Mock PlannerStore store;

    private AlarmDispatchCleanupService service;

    @BeforeEach
    void setUp() {
        service = new AlarmDispatchCleanupService(store);
        ReflectionTestUtils.setField(service, "retentionDays", 7L);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("the purge cutoff is retentionDays before today")
    void purge_delegatesWithCutoff() {
        when(store.deleteExpiredAlarmDispatches(any())).thenReturn(5);

        service.purgeExpiredDispatches();

        verify(store).deleteExpiredAlarmDispatches(LocalDate.of(2099, 6, 8));
    }

    @Test
    @DisplayName("a purge that finds nothing still completes quietly")
    void purge_nothingExpired_noop() {
        when(store.deleteExpiredAlarmDispatches(any())).thenReturn(0);

        service.purgeExpiredDispatches();

        verify(store).deleteExpiredAlarmDispatches(any());
    }
}
