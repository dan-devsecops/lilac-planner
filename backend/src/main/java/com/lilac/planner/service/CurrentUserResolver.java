package com.lilac.planner.service;

import com.lilac.planner.config.AuthProperties;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final PlannerStore store;
    private final AuthProperties auth;

    public CurrentUserResolver(PlannerStore store, AuthProperties auth) {
        this.store = store;
        this.auth = auth;
    }

    /**
     * Resolve the authenticated user. With no auth (or an absent token), returns a fixed
     * "dev" user suitable for local development. With keycloak or native auth the username is
     * read from the JWT - both providers issue {@code preferred_username} / {@code name} claims,
     * so a single code path serves both.
     */
    public User resolve(@Nullable Jwt jwt) {
        if (auth.isNone() || jwt == null) {
            return store.findUserByUsername("dev")
                    .orElseGet(() -> store.createUser("dev", "Dev User"));
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) username = jwt.getSubject();
        String displayName = jwt.getClaimAsString("name");
        if (displayName == null || displayName.isBlank()) displayName = username;
        final String u = username;
        final String d = displayName;
        return store.findUserByUsername(u).orElseGet(() -> store.createUser(u, d));
    }
}
