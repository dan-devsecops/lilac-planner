package com.lilac.planner.contract;

import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.persistence.neo4j.Neo4jPlannerStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the {@link PlannerStoreContractTest} against the Neo4j adapter on a
 * real Neo4j started by Testcontainers. Skipped automatically when Docker
 * is not available (e.g. constrained CI runners or bare laptops).
 */
@DataNeo4jTest
@ActiveProfiles("neo4j")
@Import(Neo4jPlannerStore.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PlannerStore contract - Neo4j (Testcontainers)")
class Neo4jPlannerStoreContractIT extends PlannerStoreContractTest {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.26");

    @DynamicPropertySource
    static void neo4jProps(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", neo4j::getAdminPassword);
    }

    /**
     * Mirror the unique constraints {@code Neo4jInit} creates at application startup
     * ({@code @DataNeo4jTest} never fires it). The concurrent getOrCreateDay contract
     * depends on the database rejecting a duplicate (userId, date) insert, and
     * markAlarmDispatched's dedup depends on the (userId, date, taskId) constraint.
     */
    @BeforeAll
    static void createConstraints() {
        try (Driver driver = GraphDatabase.driver(neo4j.getBoltUrl(),
                AuthTokens.basic("neo4j", neo4j.getAdminPassword()));
             Session session = driver.session()) {
            session.run("""
                CREATE CONSTRAINT day_user_date_unique IF NOT EXISTS
                FOR (d:Day) REQUIRE (d.userId, d.date) IS UNIQUE
                """).consume();
            session.run("""
                CREATE CONSTRAINT push_subscription_user_token_unique IF NOT EXISTS
                FOR (p:PushSubscription) REQUIRE (p.userId, p.token) IS UNIQUE
                """).consume();
            session.run("""
                CREATE CONSTRAINT alarm_dispatch_unique IF NOT EXISTS
                FOR (a:AlarmDispatch) REQUIRE (a.userId, a.date, a.taskId) IS UNIQUE
                """).consume();
        }
    }

    @Autowired
    PlannerStore store;

    @Override
    protected PlannerStore store() {
        return store;
    }
}
