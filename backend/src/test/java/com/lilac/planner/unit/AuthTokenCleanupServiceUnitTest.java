package com.lilac.planner.unit;

import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.AuthTokenCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthTokenCleanupService - hourly expired-token purge")
class AuthTokenCleanupServiceUnitTest {

    @Mock PlannerStore store;
    @InjectMocks AuthTokenCleanupService service;

    @Test
    @DisplayName("the purge delegates to the store with the current time")
    void purge_delegatesWithNow() {
        when(store.deleteExpiredAuthTokens(any())).thenReturn(3);
        Instant before = Instant.now();

        service.purgeExpiredTokens();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(store).deleteExpiredAuthTokens(cutoff.capture());
        assertThat(cutoff.getValue())
                .isBetween(before.minus(Duration.ofSeconds(5)), Instant.now().plus(Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("a purge that finds nothing still completes quietly")
    void purge_nothingExpired_noop() {
        when(store.deleteExpiredAuthTokens(any())).thenReturn(0);

        service.purgeExpiredTokens();

        verify(store).deleteExpiredAuthTokens(any());
    }
}
