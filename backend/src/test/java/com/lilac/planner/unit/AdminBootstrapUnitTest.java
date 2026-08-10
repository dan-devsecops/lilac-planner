package com.lilac.planner.unit;

import com.lilac.planner.domain.Role;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.AdminBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminBootstrap - startup admin seeding")
class AdminBootstrapUnitTest {

    @Mock PlannerStore store;
    @Mock PasswordEncoder encoder;

    private AdminBootstrap bootstrap(String username, String password, String email) {
        return new AdminBootstrap(store, encoder, username, password, email);
    }

    private static User user(String id, String username, String... roles) {
        User u = new User(id, username, username);
        u.setRoles(List.of(roles));
        return u;
    }

    @Test
    @DisplayName("creates the admin when configured and none exists")
    void createsAdmin() {
        when(store.listUsers()).thenReturn(List.of());
        when(store.findUserByUsername("admin")).thenReturn(Optional.empty());
        when(store.findUserByEmail("admin@corp.com")).thenReturn(Optional.empty());
        when(encoder.encode("secret123")).thenReturn("hashed");

        bootstrap("admin", "secret123", "admin@corp.com").run(null);

        verify(store).createNativeUser("admin", "admin@corp.com", "admin", "hashed", List.of(Role.ADMIN));
    }

    @Test
    @DisplayName("defaults the email when none is configured")
    void defaultsEmail() {
        when(store.listUsers()).thenReturn(List.of());
        when(store.findUserByUsername("admin")).thenReturn(Optional.empty());
        when(store.findUserByEmail("admin@local")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hashed");

        bootstrap("admin", "secret123", "").run(null);

        verify(store).createNativeUser(eq("admin"), eq("admin@local"), eq("admin"), any(), eq(List.of(Role.ADMIN)));
    }

    @Test
    @DisplayName("does nothing when credentials are not configured")
    void noopWhenUnconfigured() {
        bootstrap("", "", "").run(null);
        verify(store, never()).createNativeUser(any(), any(), any(), any(), any());
        verify(store, never()).listUsers();
    }

    @Test
    @DisplayName("does nothing when an admin already exists")
    void noopWhenAdminExists() {
        when(store.listUsers()).thenReturn(List.of(user("1", "root", Role.ADMIN)));

        bootstrap("admin", "secret123", "admin@corp.com").run(null);

        verify(store, never()).createNativeUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("skips when the username is already taken")
    void skipsWhenUsernameTaken() {
        when(store.listUsers()).thenReturn(List.of(user("1", "bob", Role.USER)));
        when(store.findUserByUsername("admin")).thenReturn(Optional.of(user("2", "admin", Role.USER)));

        bootstrap("admin", "secret123", "admin@corp.com").run(null);

        verify(store, never()).createNativeUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("skips when the email is already taken")
    void skipsWhenEmailTaken() {
        when(store.listUsers()).thenReturn(List.of(user("1", "bob", Role.USER)));
        when(store.findUserByUsername("admin")).thenReturn(Optional.empty());
        when(store.findUserByEmail("admin@corp.com")).thenReturn(Optional.of(user("3", "other", Role.USER)));

        bootstrap("admin", "secret123", "admin@corp.com").run(null);

        verify(store, never()).createNativeUser(any(), any(), any(), any(), any());
    }
}
