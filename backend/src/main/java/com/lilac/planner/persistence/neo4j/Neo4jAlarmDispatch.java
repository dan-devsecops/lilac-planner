package com.lilac.planner.persistence.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDate;

/**
 * Dedup marker for an already-sent task alarm push. Uniqueness of (userId, date, taskId)
 * is enforced by a composite constraint (see {@code Neo4jInit}), mirroring how {@code Day}
 * enforces (userId, date) - a repeat {@code save()} for the same triple throws a
 * constraint violation instead of creating a duplicate node.
 */
@Node("AlarmDispatch")
public class Neo4jAlarmDispatch {

    @Id
    @GeneratedValue(UuidV7Generator.class)
    private String id;

    private String userId;
    private LocalDate date;
    private String taskId;

    public Neo4jAlarmDispatch() {}

    public Neo4jAlarmDispatch(String userId, LocalDate date, String taskId) {
        this.userId = userId;
        this.date = date;
        this.taskId = taskId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
}
