package com.lilac.planner.service;

import com.lilac.planner.persistence.PlannerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Hourly purge of expired auth tokens. Active only when {@code planner.auth.provider=native}
 * - the only mode that persists tokens.
 *
 * <p>Without this, abandoned refresh tokens and unused password-reset tokens would accumulate
 * forever: rows are otherwise deleted only when the exact token is presented again.</p>
 */
@Service
@ConditionalOnProperty(name = "planner.auth.provider", havingValue = "native")
public class AuthTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenCleanupService.class);

    private final PlannerStore store;

    public AuthTokenCleanupService(PlannerStore store) {
        this.store = store;
    }

    @Scheduled(initialDelayString = "PT10M", fixedRateString = "PT1H")
    public void purgeExpiredTokens() {
        int purged = store.deleteExpiredAuthTokens(Instant.now());
        if (purged > 0) {
            log.info("Purged {} expired auth token(s)", purged);
        } else {
            log.debug("No expired auth tokens to purge");
        }
    }
}
