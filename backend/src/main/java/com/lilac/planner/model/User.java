package com.lilac.planner.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A user of the planner. No persistence annotations - this is the pure domain shape. */
public class User {
    private String id;
    private String username;
    private String displayName;
    /** Native-auth fields. Empty/null for keycloak and dev users. */
    private String email;
    private String passwordHash;
    private List<String> roles = new ArrayList<>();
    private Instant createdAt = Instant.now();
    /** IANA timezone identifier (e.g. "Europe/Prague"); null until the client reports one. */
    private String timezone;

    public User() {}

    public User(String id, String username, String displayName) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles == null ? new ArrayList<>() : roles; }

    public boolean hasRole(String role) { return roles != null && roles.contains(role); }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
