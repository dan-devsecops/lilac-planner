package com.lilac.planner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Profile("neo4j")
@Component
public class Neo4jInit {

    private static final Logger log = LoggerFactory.getLogger(Neo4jInit.class);

    private final Neo4jClient client;

    public Neo4jInit(Neo4jClient client) {
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        logAllConstraints("before cleanup");
        int dropped = dropLegacyDayDateConstraints();
        int purged = purgeLegacyData();
        ensureCurrentConstraints();
        logAllConstraints("after cleanup");
        log.info("Neo4j init: done - dropped {} legacy constraint(s), purged {} legacy node(s).",
                dropped, purged);
    }

    /** Public so {@code Neo4jPlannerStore} can call into it if a stale constraint slips past startup. */
    public int dropLegacyDayDateConstraints() {
        int count = 0;
        try {
            Collection<Map<String, Object>> rows = client
                    .query("SHOW CONSTRAINTS YIELD name, entityType, labelsOrTypes, properties")
                    .fetch().all();

            List<String> toDrop = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String entityType = String.valueOf(row.get("entityType"));
                List<String> labels = asStringList(row.get("labelsOrTypes"));
                List<String> props  = asStringList(row.get("properties"));
                if ("NODE".equals(entityType)
                        && labels.contains("Day")
                        && props.size() == 1
                        && "date".equals(props.get(0))) {
                    toDrop.add(String.valueOf(row.get("name")));
                }
            }

            for (String name : toDrop) {
                if (dropConstraint(name)) count++;
            }
        } catch (Exception e) {
            log.warn("Neo4j init: legacy-constraint scan failed: {}", e.getMessage());
        }
        return count;
    }

    private boolean dropConstraint(String name) {
        log.info("Neo4j init: dropping legacy constraint '{}' (uniqueness on Day.date alone)", name);
        try {
            client.query("DROP CONSTRAINT `" + name + "` IF EXISTS").run();
            return true;
        } catch (Exception perDrop) {
            log.warn("Neo4j init: could not drop constraint '{}': {}", name, perDrop.getMessage());
            return false;
        }
    }

    /**
     * Wipe pre-multi-user nodes from the database. These are Day/Task/User nodes
     * created before the schema gained {@code userId} and string {@code id}
     * properties. They cannot be addressed by the new code path (queries look up
     * by {@code id}, which they don't have) and they keep the legacy date-only
     * uniqueness from being satisfiable for new users on the same date.
     */
    private int purgeLegacyData() {
        int total = 0;
        total += countAndRun("MATCH (d:Day)  WHERE d.userId   IS NULL OR d.id IS NULL DETACH DELETE d RETURN count(d) AS n");
        total += countAndRun("MATCH (t:Task) WHERE NOT (t)<-[:HAS_TASK]-() OR t.id IS NULL DETACH DELETE t RETURN count(t) AS n");
        total += countAndRun("MATCH (u:User) WHERE u.username IS NULL OR u.id IS NULL DETACH DELETE u RETURN count(u) AS n");
        if (total > 0) log.info("Neo4j init: purged {} pre-multi-user node(s).", total);
        return total;
    }

    private int countAndRun(String cypher) {
        try {
            Collection<Map<String, Object>> rows = client.query(cypher).fetch().all();
            if (rows.isEmpty()) return 0;
            Object n = rows.iterator().next().get("n");
            return n instanceof Number num ? num.intValue() : 0;
        } catch (Exception e) {
            log.warn("Neo4j init: purge step failed [{}]: {}", cypher.strip(), e.getMessage());
            return 0;
        }
    }

    private void ensureCurrentConstraints() {
        runQuiet("""
            CREATE CONSTRAINT day_user_date_unique IF NOT EXISTS
            FOR (d:Day) REQUIRE (d.userId, d.date) IS UNIQUE
            """);
        runQuiet("""
            CREATE CONSTRAINT user_username_unique IF NOT EXISTS
            FOR (u:User) REQUIRE u.username IS UNIQUE
            """);
        runQuiet("""
            CREATE CONSTRAINT push_subscription_user_token_unique IF NOT EXISTS
            FOR (p:PushSubscription) REQUIRE (p.userId, p.token) IS UNIQUE
            """);
        runQuiet("""
            CREATE CONSTRAINT alarm_dispatch_unique IF NOT EXISTS
            FOR (a:AlarmDispatch) REQUIRE (a.userId, a.date, a.taskId) IS UNIQUE
            """);
    }

    private void logAllConstraints(String when) {
        try {
            Collection<Map<String, Object>> rows = client
                    .query("SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties")
                    .fetch().all();
            log.info("Neo4j init: {} - {} constraint(s):", when, rows.size());
            for (Map<String, Object> row : rows) {
                log.info("    name='{}' type={} entityType={} labels={} properties={}",
                        row.get("name"), row.get("type"), row.get("entityType"),
                        row.get("labelsOrTypes"), row.get("properties"));
            }
        } catch (Exception e) {
            log.warn("Neo4j init: SHOW CONSTRAINTS failed: {}", e.getMessage());
        }
    }

    private void runQuiet(String cypher) {
        try {
            client.query(cypher).run();
        } catch (Exception e) {
            log.warn("Neo4j init step failed: {}\n  query: {}", e.getMessage(), cypher.strip());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        }
        return List.of();
    }
}
