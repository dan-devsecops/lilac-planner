package com.lilac.planner.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Dedup marker keyed by its natural (userId, date, taskId) composite - the primary key
 * itself is what makes {@code markAlarmDispatched} idempotent: a repeat insert violates
 * the PK constraint instead of creating a duplicate row.
 *
 * <p>Implements {@link Persistable} and always reports {@code isNew() == true}: with a
 * manually-assigned (non-generated) composite id, Spring Data's default heuristic would
 * otherwise treat a non-null id as "existing" and route {@code save()} through
 * {@code merge()} - silently upserting a duplicate instead of throwing the constraint
 * violation the dedup check on repeat calls relies on.</p>
 */
@Entity
@Table(name = "planner_alarm_dispatch")
@IdClass(JpaAlarmDispatch.Key.class)
public class JpaAlarmDispatch implements Persistable<JpaAlarmDispatch.Key> {

    @Id
    @Column(name = "user_id", length = 80)
    private String userId;

    @Id
    @Column(name = "dispatch_date")
    private LocalDate date;

    @Id
    @Column(name = "task_id", length = 64)
    private String taskId;

    public JpaAlarmDispatch() {}

    public JpaAlarmDispatch(String userId, LocalDate date, String taskId) {
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

    @Override
    public Key getId() {
        return new Key(userId, date, taskId);
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public static class Key implements Serializable {
        private String userId;
        private LocalDate date;
        private String taskId;

        public Key() {}

        public Key(String userId, LocalDate date, String taskId) {
            this.userId = userId;
            this.date = date;
            this.taskId = taskId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId)
                    && Objects.equals(date, key.date)
                    && Objects.equals(taskId, key.taskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, date, taskId);
        }
    }
}
