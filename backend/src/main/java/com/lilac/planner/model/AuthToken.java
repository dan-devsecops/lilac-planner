package com.lilac.planner.model;

import com.lilac.planner.domain.AuthTokenType;

import java.time.Instant;

/**
 * A persisted, hashed auth token (refresh or password-reset). Only the SHA-256 {@code tokenHash}
 * of the opaque token is ever stored - the raw value lives only in the client / email link.
 * No persistence annotations - mapped to adapter-specific entities inside each adapter package.
 */
public class AuthToken {
    private AuthTokenType type;
    private String tokenHash;
    private String userId;
    private Instant expiresAt;
    private Instant createdAt = Instant.now();

    public AuthToken() {}

    public AuthToken(AuthTokenType type, String tokenHash, String userId, Instant expiresAt) {
        this.type = type;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public AuthTokenType getType() { return type; }
    public void setType(AuthTokenType type) { this.type = type; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
