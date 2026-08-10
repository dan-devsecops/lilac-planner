package com.lilac.planner.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pure-domain Day - no persistence annotations. */
public class Day {

    private String id;
    private String userId;
    private LocalDate date;
    /**
     * Optimistic-locking version carried over from the storage adapter; {@code null}
     * for a Day that has never been persisted. Adapters write conditionally on this
     * value so a stale snapshot cannot silently overwrite a concurrent update.
     */
    private Long version;
    private List<Task> tasks = new ArrayList<>();
    private List<String> earnedStickers = new ArrayList<>();

    public Day() {}

    public Day(String userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
    }

    public int totalPoints() {
        return tasks.stream().filter(Task::isCompleted).mapToInt(Task::getPoints).sum();
    }

    public int totalAvailablePoints() {
        return tasks.stream().mapToInt(Task::getPoints).sum();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks == null ? new ArrayList<>() : tasks; }

    public List<String> getEarnedStickers() { return earnedStickers; }
    public void setEarnedStickers(List<String> earnedStickers) {
        this.earnedStickers = earnedStickers == null ? new ArrayList<>() : earnedStickers;
    }
}
