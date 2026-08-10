package com.lilac.planner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Resolves the effective {@link AuthProvider} and native-auth settings from configuration.
 *
 * <p>The provider is selected by {@code planner.auth.provider}. For backward compatibility with
 * the original boolean toggle, when {@code planner.auth.provider} is left blank the legacy
 * {@code planner.auth.enabled} flag is honoured: {@code false} → {@link AuthProvider#NONE},
 * otherwise {@link AuthProvider#KEYCLOAK}. The native provider is only ever selected explicitly,
 * so native-only beans can gate cleanly on {@code planner.auth.provider=native}.</p>
 */
@Component
public class AuthProperties {

    private final String provider;
    private final boolean legacyEnabled;

    private final String jwtSecret;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Duration resetTtl;
    private final String resetUrl;
    private final boolean signupEnabled;

    private final boolean cookieSecure;
    private final String cookiePath;

    private final String metricsUsername;
    private final String metricsPassword;

    public AuthProperties(
            @Value("${planner.auth.provider:}") String provider,
            @Value("${planner.auth.enabled:true}") boolean legacyEnabled,
            @Value("${planner.auth.native.jwt-secret:}") String jwtSecret,
            @Value("${planner.auth.native.access-ttl:PT15M}") Duration accessTtl,
            @Value("${planner.auth.native.refresh-ttl:P7D}") Duration refreshTtl,
            @Value("${planner.auth.native.reset-ttl:PT1H}") Duration resetTtl,
            @Value("${planner.auth.native.reset-url:http://localhost:5173/reset-password}") String resetUrl,
            @Value("${planner.auth.native.signup-enabled:true}") boolean signupEnabled,
            @Value("${planner.auth.native.cookie-secure:true}") boolean cookieSecure,
            @Value("${planner.auth.native.cookie-path:/api/v1/auth}") String cookiePath,
            @Value("${planner.metrics.username:}") String metricsUsername,
            @Value("${planner.metrics.password:}") String metricsPassword) {
        this.provider = provider;
        this.legacyEnabled = legacyEnabled;
        this.jwtSecret = jwtSecret;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.resetTtl = resetTtl;
        this.resetUrl = resetUrl;
        this.signupEnabled = signupEnabled;
        this.cookieSecure = cookieSecure;
        this.cookiePath = cookiePath;
        this.metricsUsername = metricsUsername;
        this.metricsPassword = metricsPassword;
    }

    /** The provider this instance runs with, after applying the legacy-flag fallback. */
    public AuthProvider effectiveProvider() {
        if (provider != null && !provider.isBlank()) {
            return AuthProvider.valueOf(provider.trim().toUpperCase());
        }
        return legacyEnabled ? AuthProvider.KEYCLOAK : AuthProvider.NONE;
    }

    public boolean isNative()   { return effectiveProvider() == AuthProvider.NATIVE; }
    public boolean isKeycloak() { return effectiveProvider() == AuthProvider.KEYCLOAK; }
    public boolean isNone()     { return effectiveProvider() == AuthProvider.NONE; }

    public String jwtSecret()      { return jwtSecret; }
    public Duration accessTtl()   { return accessTtl; }
    public Duration refreshTtl()  { return refreshTtl; }
    public Duration resetTtl()    { return resetTtl; }
    public String resetUrl()      { return resetUrl; }
    public boolean signupEnabled() { return signupEnabled; }
    /** Set to false for local HTTP dev; always true in production (HTTPS). */
    public boolean cookieSecure()  { return cookieSecure; }
    /** Must match the refresh/logout endpoints' request mapping so the browser sends the cookie back. */
    public String cookiePath()    { return cookiePath; }

    /** Dedicated Basic Auth credential for the Prometheus scrape target - see SecurityConfig. */
    public String metricsUsername() { return metricsUsername; }
    public String metricsPassword() { return metricsPassword; }
}
