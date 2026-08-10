package com.lilac.planner.model;

import java.time.LocalDate;

/**
 * Dedup record marking that a task's alarm push has already been sent for a given
 * (user, date, task) triple. No persistence annotations - mapped to adapter-specific
 * entities inside each adapter package.
 */
public class AlarmDispatch {
    private String userId;
    private LocalDate date;
    private String taskId;

    public AlarmDispatch() {}

    public AlarmDispatch(String userId, LocalDate date, String taskId) {
        this.userId = userId;
        this.date = date;
        this.taskId = taskId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
}
