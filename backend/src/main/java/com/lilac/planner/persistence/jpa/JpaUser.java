package com.lilac.planner.persistence.jpa;

import com.lilac.planner.util.Timestamps;
import com.lilac.planner.util.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planner_user")
public class JpaUser {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false, length = 80)
    private String username;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** Comma-joined role names; mapped to/from a List in the adapter. */
    @Column(length = 255)
    private String roles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Timestamps.now();

    @Column(length = 64)
    private String timezone;

    public JpaUser() {}

    @PrePersist
    private void generateId() {
        if (id == null) id = Uuids.uuidV7();
    }

    public JpaUser(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    public JpaUser(String username, String email, String displayName, String passwordHash, String roles) {
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRoles() { return roles; }
    public void setRoles(String roles) { this.roles = roles; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
