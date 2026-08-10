package com.lilac.planner.persistence.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Node("User")
public class Neo4jUser {

    @Id
    @GeneratedValue(UuidV7Generator.class)
    private String id;

    private String username;
    private String displayName;
    private String email;
    private String passwordHash;
    private List<String> roles = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private String timezone;

    public Neo4jUser() {}

    public Neo4jUser(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    public Neo4jUser(String username, String email, String displayName,
                     String passwordHash, List<String> roles) {
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roles = roles == null ? new ArrayList<>() : roles;
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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
