package com.lilac.planner.service;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Native (username + password) authentication. Active only when {@code planner.auth.provider=native}.
 *
 * <p>Access tokens are short-lived HS256 JWTs carrying {@code preferred_username}, {@code name} and
 * {@code roles} claims (so the same resource-server plumbing that validates Keycloak tokens applies).
 * Refresh and password-reset tokens are opaque random values; only their SHA-256 hashes are stored,
 * so a database leak does not expose usable tokens. Changing or resetting a password revokes all of
 * the user's refresh tokens.</p>
 */
@Service
@ConditionalOnProperty(name = "planner.auth.provider", havingValue = "native")
public class NativeAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";
    private static final String INVALID_OR_EXPIRED_RESET_TOKEN = "Invalid or expired reset token";

    private final PlannerStore store;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final MailService mailService;
    private final AuthProperties auth;

    public NativeAuthService(PlannerStore store, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                             MailService mailService, AuthProperties auth) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.mailService = mailService;
        this.auth = auth;
    }

    /** Register a new user. The first ever user becomes ADMIN; the rest become USER. */
    public synchronized User register(RegisterRequest req) {
        boolean adminExists = store.listUsers().stream().anyMatch(u -> u.hasRole(Role.ADMIN));
        if (!auth.signupEnabled() && adminExists) {
            throw new SignupDisabledException();
        }
        if (store.findUserByUsername(req.username()).isPresent()) {
            throw new UserAlreadyExistsException("Username already taken");
        }
        if (store.findUserByEmail(req.email()).isPresent()) {
            throw new UserAlreadyExistsException("Email already registered");
        }
        List<String> roles = adminExists ? List.of(Role.USER) : List.of(Role.ADMIN);
        String display = (req.displayName() == null || req.displayName().isBlank())
                ? req.username() : req.displayName();
        try {
            return store.createNativeUser(req.username(), req.email(), display,
                    passwordEncoder.encode(req.password()), roles);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException("Username or email already registered");
        }
    }

    public TokenResponse login(LoginRequest req) {
        User user = findByLogin(req.login()).orElseThrow(InvalidCredentialsException::new);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /** Rotate a refresh token: validate, delete the old one, issue a fresh access+refresh pair. */
    public TokenResponse refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        AuthToken token = store.findAuthToken(AuthTokenType.REFRESH, hash)
                .orElseThrow(() -> new InvalidTokenException(INVALID_REFRESH_TOKEN));
        if (token.isExpired(Instant.now())) {
            store.deleteAuthToken(hash);
            throw new InvalidTokenException("Refresh token expired");
        }
        User user = store.findUserById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException(INVALID_REFRESH_TOKEN));
        // The delete is the single-use gate: only the caller that actually removes the row may
        // rotate. A concurrent refresh that lost the race sees false and is rejected, so the
        // same token can never be spent twice.
        if (!store.deleteAuthToken(hash)) {
            throw new InvalidTokenException(INVALID_REFRESH_TOKEN);
        }
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        store.deleteAuthToken(sha256(refreshToken));
    }

    public void changePassword(String username, ChangePasswordRequest req) {
        User user = store.findUserByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        store.updateUserPassword(user.getId(), passwordEncoder.encode(req.newPassword()));
        store.deleteAuthTokensForUser(AuthTokenType.REFRESH, user.getId());
    }

    /** Always succeeds from the caller's view - never reveals whether an email is registered. */
    public void forgotPassword(String email) {
        store.findUserByEmail(email).ifPresent(user -> {
            // Only the most recently requested reset link is valid - stale ones would
            // otherwise pile up and stay usable until they expire.
            store.deleteAuthTokensForUser(AuthTokenType.PASSWORD_RESET, user.getId());
            String raw = generateOpaqueToken();
            store.saveAuthToken(new AuthToken(AuthTokenType.PASSWORD_RESET, sha256(raw), user.getId(),
                    Instant.now().plus(auth.resetTtl())));
            mailService.sendPasswordReset(email, auth.resetUrl() + "?token=" + raw);
        });
    }

    public void resetPassword(String token, String newPassword) {
        String hash = sha256(token);
        AuthToken stored = store.findAuthToken(AuthTokenType.PASSWORD_RESET, hash)
                .orElseThrow(() -> new InvalidTokenException(INVALID_OR_EXPIRED_RESET_TOKEN));
        if (stored.isExpired(Instant.now())) {
            store.deleteAuthToken(hash);
            throw new InvalidTokenException(INVALID_OR_EXPIRED_RESET_TOKEN);
        }
        User user = store.findUserById(stored.getUserId())
                .orElseThrow(() -> new InvalidTokenException(INVALID_OR_EXPIRED_RESET_TOKEN));
        store.updateUserPassword(user.getId(), passwordEncoder.encode(newPassword));
        store.deleteAuthToken(hash);
        store.deleteAuthTokensForUser(AuthTokenType.REFRESH, user.getId());
    }

    /** Admin-only: reset another user's password (authorization enforced at the controller). */
    public void adminResetPassword(String username, String newPassword) {
        User user = store.findUserByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + username));
        store.updateUserPassword(user.getId(), passwordEncoder.encode(newPassword));
        store.deleteAuthTokensForUser(AuthTokenType.REFRESH, user.getId());
    }

    // --- internals ---

    private Optional<User> findByLogin(String login) {
        Optional<User> byUsername = store.findUserByUsername(login);
        return byUsername.isPresent() ? byUsername : store.findUserByEmail(login);
    }

    private TokenResponse issueTokens(User user) {
        String access = mintAccessToken(user);
        String refreshRaw = generateOpaqueToken();
        store.saveAuthToken(new AuthToken(AuthTokenType.REFRESH, sha256(refreshRaw), user.getId(),
                Instant.now().plus(auth.refreshTtl())));
        return TokenResponse.bearer(access, refreshRaw, auth.accessTtl().getSeconds());
    }

    private String mintAccessToken(User user) {
        Instant now = Instant.now();
        String display = (user.getDisplayName() == null || user.getDisplayName().isBlank())
                ? user.getUsername() : user.getDisplayName();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(auth.accessTtl()))
                .claim("preferred_username", user.getUsername())
                .claim("name", display)
                .claim("roles", user.getRoles())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
