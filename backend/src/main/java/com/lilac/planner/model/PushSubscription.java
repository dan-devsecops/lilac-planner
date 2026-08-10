package com.lilac.planner.model;

import com.lilac.planner.domain.Platform;

import java.time.Instant;

/**
 * A device/browser registered to receive push alarms for a user. No persistence
 * annotations - mapped to adapter-specific entities inside each adapter package.
 *
 * <p>{@code token} is the Expo push token for {@link Platform#EXPO} or the push
 * subscription endpoint URL for {@link Platform#WEB}; {@code p256dh}/{@code auth} are
 * only populated for {@link Platform#WEB} (the Web Push encryption keys).</p>
 */
public class PushSubscription {
    private String id;
    private String userId;
    private Platform platform;
    private String token;
    private String p256dh;
    private String auth;
    private Instant createdAt = Instant.now();
    private Instant lastSeenAt = Instant.now();

    public PushSubscription() {}

    public PushSubscription(String userId, Platform platform, String token) {
        this.userId = userId;
        this.platform = platform;
        this.token = token;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

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
