package com.lilac.planner.persistence.jpa;

import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.util.Timestamps;
import com.lilac.planner.util.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "planner_task")
public class JpaTask {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private JpaDay day;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false)
    private int points = 1;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Timestamps.now();

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Recurrence recurrence = Recurrence.NONE;

    @Column(name = "recurrence_group_id", length = 64)
    private String recurrenceGroupId;

    public JpaTask() {}

    @PrePersist
    private void generateId() {
        if (id == null) id = Uuids.uuidV7();
    }

    public JpaTask(JpaDay day, String title, int points, int position) {
        this.day = day;
        this.title = title;
        this.points = points;
        this.position = position;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public JpaDay getDay() { return day; }
    public void setDay(JpaDay day) { this.day = day; }

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
