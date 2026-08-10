package com.lilac.planner.persistence.jpa;

import com.lilac.planner.util.Timestamps;
import com.lilac.planner.util.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planner_auth_token", indexes = {
        @Index(name = "idx_auth_token_hash", columnList = "token_hash"),
        @Index(name = "idx_auth_token_user", columnList = "type,user_id")
})
public class JpaAuthToken {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "token_hash", unique = true, nullable = false, length = 100)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, length = 80)
    private String userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Timestamps.now();

    public JpaAuthToken() {}

    @PrePersist
    private void generateId() {
        if (id == null) id = Uuids.uuidV7();
    }

    public JpaAuthToken(String type, String tokenHash, String userId, Instant expiresAt, Instant createdAt) {
        this.type = type;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
