package com.lilac.planner.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Routes JPA's single {@link DataSource} between the primary and a DigitalOcean
 * read replica, keyed on whether the current Spring transaction is read-only -
 * i.e. {@code @Transactional(readOnly = true)} reads (see
 * {@code JpaPlannerStore.findDaysInRange}) go to the replica, everything else
 * (including no-transaction callers like Flyway at startup) goes to the primary.
 *
 * <p>{@code PG_REPLICA_URL} is optional. Unset (local dev, mariadb-only setups,
 * a DOKS deploy with {@code enable_read_replica = false}), it falls back to the
 * primary URL and this whole class becomes a same-DB round-trip - correct, just
 * pointless until a replica actually exists.
 *
 * <p>Only {@code @Profile("postgres")} - never "jpa-test" - so the H2-backed test
 * suite keeps Spring Boot's normal single-datasource autoconfiguration untouched.
 *
 * <p>The {@link LazyConnectionDataSourceProxy} wrapper is required, not cosmetic.
 * Spring's transaction manager calls {@code doBegin()} - which, for a routing
 * DataSource, triggers the primary/replica lookup - <em>before</em> it marks the
 * transaction read-only in {@link TransactionSynchronizationManager}. Without the
 * lazy proxy deferring the real {@code getConnection()} call until the first
 * statement actually executes, every lookup would see the previous transaction's
 * read-only flag (or none) instead of the current one - the routing decision
 * would always be a call behind.
 */
@Configuration
@Profile("postgres")
public class ReadReplicaDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String primaryUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.replica-url:}")
    private String replicaUrl;

    @Bean
    DataSource primaryDataSource() {
        return hikari(primaryUrl, "planner-primary");
    }

    @Bean
    DataSource replicaDataSource() {
        return hikari(replicaUrl.isBlank() ? primaryUrl : replicaUrl, "planner-replica");
    }

    @Bean
    @Primary
    DataSource dataSource(DataSource primaryDataSource, DataSource replicaDataSource) {
        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? "replica" : "primary";
            }
        };
        routing.setTargetDataSources(Map.of("primary", primaryDataSource, "replica", replicaDataSource));
        routing.setDefaultTargetDataSource(primaryDataSource);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }

    private DataSource hikari(String url, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName(poolName);
        return new HikariDataSource(config);
    }
}
