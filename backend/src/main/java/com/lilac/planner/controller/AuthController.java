package com.lilac.planner.controller;

import com.lilac.planner.config.AuthProperties;
import com.lilac.planner.dto.AccessTokenResponse;
import com.lilac.planner.dto.AdminResetPasswordRequest;
import com.lilac.planner.dto.ChangePasswordRequest;
import com.lilac.planner.dto.ForgotPasswordRequest;
import com.lilac.planner.dto.LoginRequest;
import com.lilac.planner.dto.RefreshRequest;
import com.lilac.planner.dto.RegisterRequest;
import com.lilac.planner.dto.ResetPasswordRequest;
import com.lilac.planner.dto.TokenResponse;
import com.lilac.planner.dto.UserDto;
import com.lilac.planner.service.CurrentUserResolver;
import com.lilac.planner.service.NativeAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Native-auth endpoints. Active only when {@code planner.auth.provider=native}.
 *
 * <p>register / login / refresh / logout / forgot-password / reset-password are reachable without
 * an access token (permitted in {@code SecurityConfig}); change-password and admin/reset-password
 * require an authenticated request, the latter additionally the ADMIN role.</p>
 *
 * <p>The refresh token is delivered as an {@code HttpOnly; SameSite=Strict} cookie named
 * {@code refresh_token} - it never appears in the JSON response body and is therefore invisible
 * to JavaScript, eliminating XSS-based token theft. The access token remains in-memory on the
 * client (localStorage is still used but only for the short-lived access token).</p>
 *
 * <p><b>Mobile (body-based) mode:</b> native mobile clients have no cookie jar shared with a
 * browser, so {@code /refresh} and {@code /logout} also accept the refresh token as
 * {@code {"refreshToken": "..."}} in the JSON body - sending it there is itself the opt-in signal,
 * and it takes precedence over any cookie. {@code /login} opts in via {@code {"client": "mobile"}}.
 * In both cases the response includes the rotated refresh token in the body (still also set as a
 * cookie, which mobile HTTP clients simply ignore). The cookie-only flow used by the web app is
 * completely unchanged when these fields are omitted.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "planner.auth.provider", havingValue = "native")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";

    private final NativeAuthService auth;
    private final CurrentUserResolver currentUser;
    private final AuthProperties authProps;

    public AuthController(NativeAuthService auth, CurrentUserResolver currentUser, AuthProperties authProps) {
        this.auth = auth;
        this.currentUser = currentUser;
        this.authProps = authProps;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.from(auth.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest req,
                                                     HttpServletResponse response) {
        TokenResponse tokens = auth.login(req);
        setRefreshCookie(response, tokens.refreshToken());
        boolean mobile = "mobile".equalsIgnoreCase(req.client());
        AccessTokenResponse body = mobile
                ? AccessTokenResponse.withRefreshToken(tokens)
                : AccessTokenResponse.from(tokens);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest mobileBody,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
            HttpServletResponse response) {
        boolean mobile = mobileBody != null && mobileBody.refreshToken() != null
                && !mobileBody.refreshToken().isBlank();
        String refreshToken = mobile ? mobileBody.refreshToken() : refreshCookie;
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        TokenResponse tokens = auth.refresh(refreshToken);
        setRefreshCookie(response, tokens.refreshToken());
        AccessTokenResponse body = mobile
                ? AccessTokenResponse.withRefreshToken(tokens)
                : AccessTokenResponse.from(tokens);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest mobileBody,
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
            HttpServletResponse response) {
        String bodyToken = mobileBody != null ? mobileBody.refreshToken() : null;
        String refreshToken = (bodyToken != null && !bodyToken.isBlank()) ? bodyToken : refreshCookie;
        if (refreshToken != null && !refreshToken.isBlank()) {
            auth.logout(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(currentUser.resolve(jwt).getUsername(), req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        auth.forgotPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminResetPassword(@Valid @RequestBody AdminResetPasswordRequest req) {
        auth.adminResetPassword(req.username(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    // --- cookie helpers ---

    private void setRefreshCookie(HttpServletResponse response, String value) {
        response.addHeader("Set-Cookie", refreshCookie(value, authProps.refreshTtl().getSeconds()).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", refreshCookie("", 0).toString());
    }

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(authProps.cookieSecure())
                .sameSite("Strict")
                .path(authProps.cookiePath())
                .maxAge(maxAgeSeconds)
                .build();
    }
}
