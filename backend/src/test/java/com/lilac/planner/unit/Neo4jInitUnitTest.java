package com.lilac.planner.unit;

import com.lilac.planner.config.Neo4jInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Neo4jInit - legacy constraint cleanup")
class Neo4jInitUnitTest {

    @Mock Neo4jClient client;

    Neo4jInit init;

    /** Queries executed through the mocked client, in order. */
    final List<String> executed = new ArrayList<>();
    /** Maps a query prefix to the rows its fetch().all() returns. */
    final Map<String, Collection<Map<String, Object>>> fetchResults = new HashMap<>();
    /** Query prefixes whose run() should throw. */
    final List<String> failingRuns = new ArrayList<>();
    /** Query prefixes whose query() call itself should throw. */
    final List<String> failingQueries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        init = new Neo4jInit(client);
        when(client.query(anyString())).thenAnswer(inv -> {
            String q = inv.getArgument(0, String.class).strip();
            executed.add(q);
            if (failingQueries.stream().anyMatch(q::startsWith)) {
                throw new RuntimeException("query refused: " + q);
            }
            Neo4jClient.UnboundRunnableSpec spec =
                    mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
            Collection<Map<String, Object>> rows = fetchResults.entrySet().stream()
                    .filter(e -> q.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(List.of());
            when(spec.fetch().all()).thenReturn(rows);
            if (failingRuns.stream().anyMatch(q::startsWith)) {
                when(spec.run()).thenThrow(new RuntimeException("run refused"));
            }
            return spec;
        });
    }

    private static Map<String, Object> constraintRow(String name, String entityType,
                                                     List<String> labels, List<String> props) {
        return Map.of("name", name, "entityType", entityType,
                "labelsOrTypes", labels, "properties", props);
    }

    @Test
    @DisplayName("drops only the legacy single-property Day.date constraint")
    void dropLegacy_dropsOnlyMatching() {
        fetchResults.put("SHOW CONSTRAINTS", List.of(
                constraintRow("legacy_day_date", "NODE", List.of("Day"), List.of("date")),
                constraintRow("day_user_date_unique", "NODE", List.of("Day"), List.of("userId", "date")),
                constraintRow("user_username_unique", "NODE", List.of("User"), List.of("username")),
                constraintRow("rel_constraint", "RELATIONSHIP", List.of("Day"), List.of("date"))
        ));

        int dropped = init.dropLegacyDayDateConstraints();

        assertThat(dropped).isEqualTo(1);
        assertThat(executed)
                .anyMatch(q -> q.contains("DROP CONSTRAINT `legacy_day_date`"))
                .noneMatch(q -> q.contains("DROP CONSTRAINT `day_user_date_unique`"))
                .noneMatch(q -> q.contains("DROP CONSTRAINT `user_username_unique`"))
                .noneMatch(q -> q.contains("DROP CONSTRAINT `rel_constraint`"));
    }

    @Test
    @DisplayName("returns 0 when no constraints exist")
    void dropLegacy_noConstraints_returnsZero() {
        assertThat(init.dropLegacyDayDateConstraints()).isZero();
    }

    @Test
    @DisplayName("a failing DROP is logged and skipped, not propagated")
    void dropLegacy_dropFails_returnsZero() {
        fetchResults.put("SHOW CONSTRAINTS", List.of(
                constraintRow("legacy_day_date", "NODE", List.of("Day"), List.of("date"))
        ));
        failingRuns.add("DROP CONSTRAINT");

        assertThat(init.dropLegacyDayDateConstraints()).isZero();
    }

    @Test
    @DisplayName("a failing SHOW CONSTRAINTS scan is swallowed and returns 0")
    void dropLegacy_scanFails_returnsZero() {
        failingQueries.add("SHOW CONSTRAINTS");

        assertThat(init.dropLegacyDayDateConstraints()).isZero();
    }

    @Test
    @DisplayName("init runs the full pipeline: scan, purge, and ensure current constraints")
    void init_runsFullPipeline() {
        fetchResults.put("MATCH (d:Day)", List.of(Map.of("n", 2L)));
        fetchResults.put("MATCH (t:Task)", List.of(Map.of("n", 1)));
        fetchResults.put("MATCH (u:User)", List.of(Map.of("n", "not-a-number")));

        init.init();

        assertThat(executed)
                .anyMatch(q -> q.startsWith("MATCH (d:Day)"))
                .anyMatch(q -> q.startsWith("MATCH (t:Task)"))
                .anyMatch(q -> q.startsWith("MATCH (u:User)"))
                .anyMatch(q -> q.contains("CREATE CONSTRAINT day_user_date_unique"))
                .anyMatch(q -> q.contains("CREATE CONSTRAINT user_username_unique"));
    }

    @Test
    @DisplayName("init survives every step failing")
    void init_allStepsFail_noException() {
        failingQueries.add("SHOW CONSTRAINTS");
        failingQueries.add("MATCH");
        failingRuns.add("CREATE CONSTRAINT");

        assertThatCode(() -> init.init()).doesNotThrowAnyException();
    }
}
