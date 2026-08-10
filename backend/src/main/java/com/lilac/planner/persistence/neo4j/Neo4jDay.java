package com.lilac.planner.persistence.neo4j;

import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Node("Day")
public class Neo4jDay {

    @Id
    @GeneratedValue(UuidV7Generator.class)
    private String id;

    @Version
    private Long version;

    private String userId;
    private LocalDate date;

    @Relationship(type = "HAS_TASK", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jTask> tasks = new ArrayList<>();

    private List<String> earnedStickers = new ArrayList<>();

    public Neo4jDay() {}

    public Neo4jDay(String userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public List<Neo4jTask> getTasks() { return tasks; }
    public void setTasks(List<Neo4jTask> tasks) { this.tasks = tasks == null ? new ArrayList<>() : tasks; }

    public List<String> getEarnedStickers() { return earnedStickers; }
    public void setEarnedStickers(List<String> earnedStickers) {
        this.earnedStickers = earnedStickers == null ? new ArrayList<>() : earnedStickers;
    }
}
