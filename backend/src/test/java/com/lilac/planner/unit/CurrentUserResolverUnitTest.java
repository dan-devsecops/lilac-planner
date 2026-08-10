package com.lilac.planner.unit;

import com.lilac.planner.config.AuthProperties;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.CurrentUserResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentUserResolver - JWT to User mapping")
class CurrentUserResolverUnitTest {

    @Mock PlannerStore store;

    static final User DEV = new User("1", "dev", "Dev User");

    private static AuthProperties props(String provider) {
        return new AuthProperties(provider, true, "test-secret-test-secret-test-secret-1234",
                Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofHours(1),
                "http://localhost:5173/reset-password", true, true, "/api/v1/auth",
                "prometheus", "test-metrics-password");
    }

    private CurrentUserResolver resolver(String provider) {
        return new CurrentUserResolver(store, props(provider));
    }

    @Test
    @DisplayName("provider none + null JWT → returns existing dev user")
    void resolve_none_returnsDev() {
        when(store.findUserByUsername("dev")).thenReturn(Optional.of(DEV));

        assertThat(resolver("none").resolve(null).getUsername()).isEqualTo("dev");
        verify(store, never()).createUser(any(), any());
    }

    @Test
    @DisplayName("provider none + null JWT → creates dev user if not found")
    void resolve_none_createsDevWhenMissing() {
        when(store.findUserByUsername("dev")).thenReturn(Optional.empty());
        when(store.createUser("dev", "Dev User")).thenReturn(DEV);

        assertThat(resolver("none").resolve(null).getUsername()).isEqualTo("dev");
        verify(store).createUser("dev", "Dev User");
    }

    @Test
    @DisplayName("auth enabled + null JWT → falls back to dev user")
    void resolve_authEnabled_nullJwt_returnsDev() {
        when(store.findUserByUsername("dev")).thenReturn(Optional.of(DEV));

        assertThat(resolver("keycloak").resolve(null).getUsername()).isEqualTo("dev");
    }

    @Test
    @DisplayName("JWT with preferred_username → resolves that user")
    void resolve_jwtWithPreferredUsername_usesIt() {
        User alice = new User("2", "alice", "Alice Wonderland");
        when(store.findUserByUsername("alice")).thenReturn(Optional.of(alice));

        assertThat(resolver("keycloak").resolve(jwt("alice", "Alice Wonderland", "sub-uuid")).getUsername())
                .isEqualTo("alice");
    }

    @Test
    @DisplayName("JWT without preferred_username falls back to subject claim")
    void resolve_jwtNoPreferredUsername_fallsBackToSubject() {
        User subUser = new User("3", "sub-uuid", "sub-uuid");
        when(store.findUserByUsername("sub-uuid")).thenReturn(Optional.empty());
        when(store.createUser("sub-uuid", "sub-uuid")).thenReturn(subUser);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("sub-uuid")
                .expiresAt(Instant.now().plusSeconds(300))
                .issuedAt(Instant.now())
                .build();

        assertThat(resolver("keycloak").resolve(jwt).getUsername()).isEqualTo("sub-uuid");
    }

    @Test
    @DisplayName("unknown JWT user is auto-created on first login")
    void resolve_unknownUser_isCreated() {
        User newUser = new User("4", "bob", "Bob");
        when(store.findUserByUsername("bob")).thenReturn(Optional.empty());
        when(store.createUser("bob", "Bob")).thenReturn(newUser);

        resolver("native").resolve(jwt("bob", "Bob", "bob-uuid"));
        verify(store).createUser("bob", "Bob");
    }

    private static Jwt jwt(String preferredUsername, String name, String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("preferred_username", preferredUsername)
                .claim("name", name)
                .expiresAt(Instant.now().plusSeconds(300))
                .issuedAt(Instant.now())
                .build();
    }
}
