package com.lilac.planner.model;

import com.lilac.planner.domain.Recurrence;

import java.time.Instant;
import java.time.LocalTime;

/** Pure-domain Task - no persistence annotations. Each adapter maps to/from this. */
public class Task {

    private String id;
    private String title;
    private int points = 1;
    private boolean completed = false;
    private int position;
    private Instant createdAt = Instant.now();

    private LocalTime scheduledTime;
    private Recurrence recurrence = Recurrence.NONE;
    private String recurrenceGroupId;

    public Task() {}

    public Task(String title, int points, int position) {
        this.title = title;
        this.points = points;
        this.position = position;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public Recurrence getRecurrence() { return recurrence == null ? Recurrence.NONE : recurrence; }
    public void setRecurrence(Recurrence recurrence) { this.recurrence = recurrence; }

    public String getRecurrenceGroupId() { return recurrenceGroupId; }
    public void setRecurrenceGroupId(String recurrenceGroupId) { this.recurrenceGroupId = recurrenceGroupId; }
}
