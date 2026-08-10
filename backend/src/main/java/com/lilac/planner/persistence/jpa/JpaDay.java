package com.lilac.planner.persistence.jpa;

import com.lilac.planner.util.Uuids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "planner_day",
    uniqueConstraints = @UniqueConstraint(name = "uk_day_user_date", columnNames = {"user_id", "day_date"})
)
public class JpaDay {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "day_date", nullable = false)
    private LocalDate date;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<JpaTask> tasks = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "sticker_code")
    private List<String> earnedStickers = new ArrayList<>();

    public JpaDay() {}

    @PrePersist
    private void generateId() {
        if (id == null) id = Uuids.uuidV7();
    }

    public JpaDay(String userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public List<JpaTask> getTasks() { return tasks; }
    public void setTasks(List<JpaTask> tasks) { this.tasks = tasks == null ? new ArrayList<>() : tasks; }

    public List<String> getEarnedStickers() { return earnedStickers; }
    public void setEarnedStickers(List<String> earnedStickers) {
        this.earnedStickers = earnedStickers == null ? new ArrayList<>() : earnedStickers;
    }
}
