package com.lilac.planner.persistence.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.Instant;

@Node("PushSubscription")
public class Neo4jPushSubscription {

    @Id
    @GeneratedValue(UuidV7Generator.class)
    private String id;

    private String userId;
    private String platform;
    private String token;
    private String p256dh;
    private String auth;
    private Instant createdAt = Instant.now();
    private Instant lastSeenAt = Instant.now();

    public Neo4jPushSubscription() {}

    public Neo4jPushSubscription(String userId, String platform, String token) {
        this.userId = userId;
        this.platform = platform;
        this.token = token;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
