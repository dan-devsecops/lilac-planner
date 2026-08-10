package com.lilac.planner.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Self-healing Flyway startup.
 *
 * <p>MariaDB has no transactional DDL, so a migration that fails partway through
 * leaves a {@code success=0} row in {@code flyway_schema_history}. On every
 * subsequent startup Flyway's validate phase aborts with
 * <em>"Detected failed migration to version N"</em> <strong>before</strong> it can
 * re-run the (now corrected, idempotent) script - bricking the deploy permanently.
 *
 * <p>This strategy repairs in-process using the application's own datasource:
 * attempt {@code migrate()}; if and only if it fails <em>validation</em>, run
 * Flyway {@code repair()} (which deletes failed-migration rows and realigns
 * checksums) and migrate again. A genuine SQL error during migration is not a
 * {@link FlywayValidateException}, so it propagates and fails the deploy loudly -
 * the repair path only triggers for the failed-history / checksum case it is
 * meant to heal.
 *
 * <p>Inert outside the JPA profiles: tests disable Flyway, and the Neo4j/DynamoDB
 * profiles have no Flyway, so the strategy is never invoked there.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    FlywayMigrationStrategy repairOnValidationFailure() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException e) {
                log.warn("Flyway validation failed - running repair to clear failed "
                        + "migration history, then retrying migrate. Cause: {}", e.getMessage());
                flyway.repair();
                flyway.migrate();
            }
        };
    }
}
