package com.lilac.planner.persistence.jpa;

import com.lilac.planner.util.Timestamps;
import com.lilac.planner.util.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planner_push_subscription",
        indexes = {
                @Index(name = "idx_push_subscription_user", columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_push_subscription_user_token", columnNames = {"user_id", "token"})
        })
public class JpaPushSubscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 80)
    private String userId;

    @Column(nullable = false, length = 10)
    private String platform;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(length = 255)
    private String p256dh;

    @Column(length = 255)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Timestamps.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Timestamps.now();

    public JpaPushSubscription() {}

    @PrePersist
    private void generateId() {
        if (id == null) id = Uuids.uuidV7();
    }

    public JpaPushSubscription(String userId, String platform, String token) {
        this.userId = userId;
        this.platform = platform;
        this.token = token;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }

    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
