package com.lilac.planner.unit;

import com.lilac.planner.config.AuthProperties;
import com.lilac.planner.domain.AuthTokenType;
import com.lilac.planner.domain.Role;
import com.lilac.planner.dto.ChangePasswordRequest;
import com.lilac.planner.dto.LoginRequest;
import com.lilac.planner.dto.RegisterRequest;
import com.lilac.planner.dto.TokenResponse;
import com.lilac.planner.model.AuthToken;
import com.lilac.planner.model.User;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.AuthExceptions.InvalidCredentialsException;
import com.lilac.planner.service.AuthExceptions.InvalidTokenException;
import com.lilac.planner.service.AuthExceptions.SignupDisabledException;
import com.lilac.planner.service.AuthExceptions.UserAlreadyExistsException;
import com.lilac.planner.service.MailService;
import com.lilac.planner.service.NativeAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NativeAuthService - registration, login, tokens, password reset")
class NativeAuthServiceUnitTest {

    @Mock PlannerStore store;
    @Mock PasswordEncoder encoder;
    @Mock JwtEncoder jwtEncoder;
    @Mock MailService mail;

    NativeAuthService auth;

    private static AuthProperties props(boolean signupEnabled) {
        return new AuthProperties("native", true, "test-secret-test-secret-test-secret-1234",
                Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofHours(1),
                "http://localhost:5173/reset-password", signupEnabled, true, "/api/v1/auth",
                "prometheus", "test-metrics-password");
    }

    private static User nativeUser(String id, String username, String hash, String... roles) {
        User u = new User(id, username, username);
        u.setEmail(username + "@x.com");
        u.setPasswordHash(hash);
        u.setRoles(List.of(roles));
        return u;
    }

    @BeforeEach
    void setUp() {
        auth = new NativeAuthService(store, encoder, jwtEncoder, mail, props(true));
        Jwt jwt = Jwt.withTokenValue("access-token").header("alg", "HS256")
                .subject("x").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
        when(encoder.encode(any())).thenReturn("hashed");
        when(store.deleteAuthToken(any())).thenReturn(true);
    }

    // --- register ---

    @Test
    @DisplayName("the first registered user becomes ADMIN")
    void register_firstUser_admin() {
        when(store.listUsers()).thenReturn(List.of());
        when(store.findUserByUsername("alice")).thenReturn(Optional.empty());
        when(store.findUserByEmail("alice@x.com")).thenReturn(Optional.empty());
        when(store.createNativeUser(any(), any(), any(), any(), any()))
                .thenReturn(nativeUser("1", "alice", "hashed", Role.ADMIN));

        auth.register(new RegisterRequest("alice", "alice@x.com", "Alice", "password1"));

        ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
        verify(store).createNativeUser(eq("alice"), eq("alice@x.com"), eq("Alice"), eq("hashed"), roles.capture());
        assertThat(roles.getValue()).containsExactly(Role.ADMIN);
    }

    @Test
    @DisplayName("subsequent users become USER")
    void register_secondUser_user() {
        when(store.listUsers()).thenReturn(List.of(nativeUser("1", "admin", "h", Role.ADMIN)));
        when(store.findUserByUsername("bob")).thenReturn(Optional.empty());
        when(store.findUserByEmail("bob@x.com")).thenReturn(Optional.empty());
        when(store.createNativeUser(any(), any(), any(), any(), any()))
                .thenReturn(nativeUser("2", "bob", "hashed", Role.USER));

        auth.register(new RegisterRequest("bob", "bob@x.com", null, "password1"));

        ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
        verify(store).createNativeUser(eq("bob"), eq("bob@x.com"), eq("bob"), eq("hashed"), roles.capture());
        assertThat(roles.getValue()).containsExactly(Role.USER);
    }

    @Test
    @DisplayName("a taken username is rejected")
    void register_duplicateUsername_throws() {
        when(store.listUsers()).thenReturn(List.of(nativeUser("1", "admin", "h", Role.ADMIN)));
        when(store.findUserByUsername("alice")).thenReturn(Optional.of(nativeUser("9", "alice", "h", Role.USER)));

        RegisterRequest req = new RegisterRequest("alice", "alice@x.com", "Alice", "password1");
        assertThatThrownBy(() -> auth.register(req))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("a taken email is rejected")
    void register_duplicateEmail_throws() {
        when(store.listUsers()).thenReturn(List.of(nativeUser("1", "admin", "h", Role.ADMIN)));
        when(store.findUserByUsername("alice")).thenReturn(Optional.empty());
        when(store.findUserByEmail("alice@x.com")).thenReturn(Optional.of(nativeUser("9", "other", "h", Role.USER)));

        RegisterRequest req = new RegisterRequest("alice", "alice@x.com", "Alice", "password1");
        assertThatThrownBy(() -> auth.register(req))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    @DisplayName("registration is blocked when signup is disabled and an admin already exists")
    void register_signupDisabled_throws() {
        auth = new NativeAuthService(store, encoder, jwtEncoder, mail, props(false));
        when(store.listUsers()).thenReturn(List.of(nativeUser("1", "admin", "h", Role.ADMIN)));

        RegisterRequest req = new RegisterRequest("bob", "bob@x.com", "Bob", "password1");
        assertThatThrownBy(() -> auth.register(req))
                .isInstanceOf(SignupDisabledException.class);
    }

    @Test
    @DisplayName("the very first user is allowed even when signup is disabled")
    void register_signupDisabled_firstAdminAllowed() {
        auth = new NativeAuthService(store, encoder, jwtEncoder, mail, props(false));
        when(store.listUsers()).thenReturn(List.of());
        when(store.findUserByUsername("root")).thenReturn(Optional.empty());
        when(store.findUserByEmail("root@x.com")).thenReturn(Optional.empty());
        when(store.createNativeUser(any(), any(), any(), any(), any()))
                .thenReturn(nativeUser("1", "root", "hashed", Role.ADMIN));

        assertThat(auth.register(new RegisterRequest("root", "root@x.com", "Root", "password1")).getUsername())
                .isEqualTo("root");
    }

    // --- login ---

    @Test
    @DisplayName("login with valid credentials issues access + refresh tokens")
    void login_success() {
        User u = nativeUser("1", "alice", "stored-hash", Role.USER);
        when(store.findUserByUsername("alice")).thenReturn(Optional.of(u));
        when(encoder.matches("password1", "stored-hash")).thenReturn(true);

        TokenResponse out = auth.login(new LoginRequest("alice", "password1", null));

        assertThat(out.accessToken()).isEqualTo("access-token");
        assertThat(out.refreshToken()).isNotBlank();
        assertThat(out.tokenType()).isEqualTo("Bearer");
        assertThat(out.expiresIn()).isEqualTo(900);
        verify(store).saveAuthToken(any(AuthToken.class));
    }

    @Test
    @DisplayName("login falls back to email lookup when username is not found")
    void login_byEmail() {
        User u = nativeUser("1", "alice", "stored-hash", Role.USER);
        when(store.findUserByUsername("alice@x.com")).thenReturn(Optional.empty());
        when(store.findUserByEmail("alice@x.com")).thenReturn(Optional.of(u));
        when(encoder.matches("password1", "stored-hash")).thenReturn(true);

        assertThat(auth.login(new LoginRequest("alice@x.com", "password1", null)).accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("login with a wrong password is rejected")
    void login_wrongPassword() {
        User u = nativeUser("1", "alice", "stored-hash", Role.USER);
        when(store.findUserByUsername("alice")).thenReturn(Optional.of(u));
        when(encoder.matches("nope", "stored-hash")).thenReturn(false);

        LoginRequest req = new LoginRequest("alice", "nope", null);
        assertThatThrownBy(() -> auth.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(store, never()).saveAuthToken(any());
    }

    @Test
    @DisplayName("login for an unknown user is rejected")
    void login_unknownUser() {
        when(store.findUserByUsername("ghost")).thenReturn(Optional.empty());
        when(store.findUserByEmail("ghost")).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest("ghost", "x", null);
        assertThatThrownBy(() -> auth.login(req))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // --- refresh ---

    @Test
    @DisplayName("refresh rotates the token: deletes the old, issues a new pair")
    void refresh_rotates() {
        AuthToken stored = new AuthToken(AuthTokenType.REFRESH, "ignored", "1", Instant.now().plusSeconds(3600));
        when(store.findAuthToken(eq(AuthTokenType.REFRESH), any())).thenReturn(Optional.of(stored));
        when(store.findUserById("1")).thenReturn(Optional.of(nativeUser("1", "alice", "h", Role.USER)));

        TokenResponse out = auth.refresh("some-refresh-token");

        assertThat(out.accessToken()).isEqualTo("access-token");
        verify(store).deleteAuthToken(any());     // old token removed
        verify(store).saveAuthToken(any());        // new token stored
    }

    @Test
    @DisplayName("an expired refresh token is deleted and rejected")
    void refresh_expired() {
        AuthToken stored = new AuthToken(AuthTokenType.REFRESH, "ignored", "1", Instant.now().minusSeconds(10));
        when(store.findAuthToken(eq(AuthTokenType.REFRESH), any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> auth.refresh("token")).isInstanceOf(InvalidTokenException.class);
        verify(store).deleteAuthToken(any());
        verify(store, never()).saveAuthToken(any());
    }

    @Test
    @DisplayName("refresh is rejected when the token was already spent concurrently (delete reports false)")
    void refresh_doubleSpend_rejected() {
        AuthToken stored = new AuthToken(AuthTokenType.REFRESH, "ignored", "1", Instant.now().plusSeconds(3600));
        when(store.findAuthToken(eq(AuthTokenType.REFRESH), any())).thenReturn(Optional.of(stored));
        when(store.findUserById("1")).thenReturn(Optional.of(nativeUser("1", "alice", "h", Role.USER)));
        when(store.deleteAuthToken(any())).thenReturn(false); // a concurrent refresh won the race

        assertThatThrownBy(() -> auth.refresh("some-refresh-token")).isInstanceOf(InvalidTokenException.class);
        verify(store, never()).saveAuthToken(any()); // no new pair issued
    }

    @Test
    @DisplayName("an unknown refresh token is rejected")
    void refresh_unknown() {
        when(store.findAuthToken(eq(AuthTokenType.REFRESH), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.refresh("token")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("logout deletes the presented refresh token")
    void logout_deletes() {
        auth.logout("token");
        verify(store).deleteAuthToken(any());
    }

    // --- change password ---

    @Test
    @DisplayName("change-password updates the hash and revokes refresh tokens")
    void changePassword_success() {
        User u = nativeUser("1", "alice", "old-hash", Role.USER);
        when(store.findUserByUsername("alice")).thenReturn(Optional.of(u));
        when(encoder.matches("oldpass", "old-hash")).thenReturn(true);

        auth.changePassword("alice", new ChangePasswordRequest("oldpass", "newpass12"));

        verify(store).updateUserPassword(eq("1"), any());
        verify(store).deleteAuthTokensForUser(AuthTokenType.REFRESH, "1");
    }

    @Test
    @DisplayName("change-password with a wrong current password is rejected")
    void changePassword_wrongCurrent() {
        User u = nativeUser("1", "alice", "old-hash", Role.USER);
        when(store.findUserByUsername("alice")).thenReturn(Optional.of(u));
        when(encoder.matches("bad", "old-hash")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest("bad", "newpass12");
        assertThatThrownBy(() -> auth.changePassword("alice", req))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(store, never()).updateUserPassword(any(), any());
    }

    // --- forgot / reset ---

    @Test
    @DisplayName("forgot-password stores a reset token and emails the link for a known email")
    void forgotPassword_known() {
        when(store.findUserByEmail("alice@x.com")).thenReturn(Optional.of(nativeUser("1", "alice", "h", Role.USER)));

        auth.forgotPassword("alice@x.com");

        ArgumentCaptor<AuthToken> token = ArgumentCaptor.forClass(AuthToken.class);
        verify(store).saveAuthToken(token.capture());
        assertThat(token.getValue().getType()).isEqualTo(AuthTokenType.PASSWORD_RESET);
        verify(mail).sendPasswordReset(eq("alice@x.com"), any());
    }

    @Test
    @DisplayName("forgot-password invalidates prior reset tokens before saving the new one")
    void forgotPassword_purgesPriorResetTokens() {
        when(store.findUserByEmail("alice@x.com")).thenReturn(Optional.of(nativeUser("1", "alice", "h", Role.USER)));

        auth.forgotPassword("alice@x.com");

        InOrder inOrder = inOrder(store);
        inOrder.verify(store).deleteAuthTokensForUser(AuthTokenType.PASSWORD_RESET, "1");
        inOrder.verify(store).saveAuthToken(any(AuthToken.class));
    }

    @Test
    @DisplayName("forgot-password for an unknown email does nothing (no enumeration)")
    void forgotPassword_unknown() {
        when(store.findUserByEmail("ghost@x.com")).thenReturn(Optional.empty());

        auth.forgotPassword("ghost@x.com");

        verify(store, never()).saveAuthToken(any());
        verify(mail, never()).sendPasswordReset(any(), any());
    }

    @Test
    @DisplayName("reset-password sets a new password, consumes the token, revokes refresh tokens")
    void resetPassword_success() {
        AuthToken stored = new AuthToken(AuthTokenType.PASSWORD_RESET, "ignored", "1", Instant.now().plusSeconds(600));
        when(store.findAuthToken(eq(AuthTokenType.PASSWORD_RESET), any())).thenReturn(Optional.of(stored));
        when(store.findUserById("1")).thenReturn(Optional.of(nativeUser("1", "alice", "h", Role.USER)));

        auth.resetPassword("reset-token", "newpass12");

        verify(store).updateUserPassword(eq("1"), any());
        verify(store).deleteAuthToken(any());
        verify(store).deleteAuthTokensForUser(AuthTokenType.REFRESH, "1");
    }

    @Test
    @DisplayName("reset-password with an expired token is rejected")
    void resetPassword_expired() {
        AuthToken stored = new AuthToken(AuthTokenType.PASSWORD_RESET, "ignored", "1", Instant.now().minusSeconds(1));
        when(store.findAuthToken(eq(AuthTokenType.PASSWORD_RESET), any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> auth.resetPassword("token", "newpass12")).isInstanceOf(InvalidTokenException.class);
        verify(store, never()).updateUserPassword(any(), any());
    }

    @Test
    @DisplayName("reset-password with an unknown token is rejected")
    void resetPassword_unknown() {
        when(store.findAuthToken(eq(AuthTokenType.PASSWORD_RESET), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.resetPassword("token", "newpass12")).isInstanceOf(InvalidTokenException.class);
    }

    // --- admin reset ---

    @Test
    @DisplayName("admin reset updates the target's password and revokes their refresh tokens")
    void adminReset_success() {
        when(store.findUserByUsername("bob")).thenReturn(Optional.of(nativeUser("2", "bob", "h", Role.USER)));

        auth.adminResetPassword("bob", "newpass12");

        verify(store).updateUserPassword(eq("2"), any());
        verify(store).deleteAuthTokensForUser(AuthTokenType.REFRESH, "2");
    }

    @Test
    @DisplayName("admin reset for an unknown user fails")
    void adminReset_unknown() {
        when(store.findUserByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.adminResetPassword("ghost", "newpass12"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
