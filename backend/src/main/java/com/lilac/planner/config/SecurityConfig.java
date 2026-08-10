package com.lilac.planner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * One provider-aware filter chain backs all three auth modes (see {@link AuthProvider}):
 * <ul>
 *   <li><b>none</b> - permit everything; {@code CurrentUserResolver} returns a fixed "dev" user.</li>
 *   <li><b>keycloak</b> - validate Keycloak JWTs via a JWK decoder that also enforces the
 *       expected issuer and audience (see {@link #jwtDecoder}).</li>
 *   <li><b>native</b> - validate our own HS256 JWTs and expose {@code /api/v1/auth/**} unauthenticated.</li>
 * </ul>
 * Native and Keycloak are mutually exclusive - the same {@code @AuthenticationPrincipal Jwt}
 * plumbing serves both, so {@code CurrentUserResolver} needs no per-provider branching.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${planner.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${planner.auth.keycloak.issuer}")
    private String keycloakIssuer;

    @Value("${planner.auth.keycloak.audience}")
    private String keycloakAudience;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthProperties auth, JwtDecoder jwtDecoder)
            throws Exception {
        // CSRF-disable hotspot, reviewed safe: this chain is STATELESS (no session cookie) and
        // the only authentication mechanism wired below is oauth2ResourceServer JWT bearer -
        // no httpBasic()/formLogin(), no cookie ever carries auth. CSRF relies on the browser
        // auto-attaching ambient credentials (cookies); a Bearer token in an Authorization
        // header must be set explicitly by same-origin JS (localStorage-held, per
        // src/auth/nativeAuth.js), so a cross-site request can't replay it. See b9a20aa for the
        // metrics chain case where this combination (httpBasic + no explicit STATELESS) was
        // actually unsafe.
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults());

        if (auth.isNone()) {
            http.authorizeHttpRequests(reg -> reg.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(reg -> {
            // Health and info are harmless and required by the CD pipeline / load-balancer
            // healthchecks - keep them public.
            reg.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
            // App-version gate: a mobile app on a version below minSupportedAppVersion must be
            // able to learn that before it can authenticate.
            reg.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/meta").permitAll();
            // VAPID public key: a web client must be able to build its PushManager subscription
            // before it necessarily has an access token.
            reg.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/push/vapid-public-key")
                    .permitAll();
            // Prometheus exposes JVM internals, DB pool state, and request rates.
            // In native mode restrict to ADMIN; in keycloak mode any authenticated user suffices
            // (the JWT already proves identity). Any other exposed actuator endpoint follows
            // the same rule so future additions don't accidentally become public.
            if (auth.isNative()) {
                reg.requestMatchers("/actuator/**").hasRole("ADMIN");
            } else {
                reg.requestMatchers("/actuator/**").authenticated();
            }
            if (auth.isNative()) {
                // Registration, login, token refresh and password reset must be reachable
                // without an access token. change-password and admin/** stay authenticated.
                reg.requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                        "/api/v1/auth/logout", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password")
                        .permitAll();
            }
            reg.anyRequest().authenticated();
        });

        http.oauth2ResourceServer(oauth2 -> {
            if (auth.isNative()) {
                oauth2.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(nativeJwtAuthConverter()));
            } else {
                oauth2.jwt(jwt -> jwt.decoder(jwtDecoder));
            }
        });
        return http.build();
    }

    /**
     * Prometheus scrapes {@code /actuator/prometheus} unauthenticated - it can't hold a JWT,
     * whether user-issued (native) or IdP-issued (keycloak) - so that one path gets its own
     * filter chain, evaluated before {@link #filterChain}, gated by a dedicated Basic Auth
     * credential instead of piggybacking on either auth mode's user tokens. Skipped (permitAll)
     * under {@code none}, matching that mode's "everything is open" contract.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain metricsFilterChain(HttpSecurity http, AuthProperties auth) throws Exception {
        http.securityMatcher("/actuator/prometheus")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (auth.isNone()) {
            http.authorizeHttpRequests(reg -> reg.anyRequest().permitAll());
            return http.build();
        }
        http.httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(reg -> reg.anyRequest().authenticated());
        return http.build();
    }

    /**
     * This bean is created unconditionally (Spring Security always needs a
     * {@code UserDetailsService} to wire up {@code httpBasic()}), unlike the native-only JWT
     * beans below that only exist under {@code planner.auth.provider=native} - so unlike
     * {@link #nativeSecretKey}, it must not throw when unconfigured, or it would break every
     * {@code @SpringBootTest} context that isn't explicitly {@code none} mode, not just real
     * deployments. When the credential is blank, no user is registered at all: Basic Auth then
     * simply can't succeed for anyone (fail-closed, same effective outcome as a hard error,
     * without crashing startup). Real deployments are still guaranteed to have it set - that
     * enforcement lives at the Compose layer (see {@code docker-compose.prod.yml},
     * {@code docker-compose-native.yml}, {@code docker-compose-keycloak.yml}), the same place
     * every other required secret in this project (DB password, JWT secret) is enforced.
     */
    @Bean
    public UserDetailsService metricsUserDetailsService(AuthProperties auth, PasswordEncoder encoder) {
        String username = auth.metricsUsername();
        String password = auth.metricsPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(encoder.encode(password)).roles("METRICS").build());
    }

    /** Maps the {@code roles} claim of a native JWT to {@code ROLE_*} authorities. */
    private JwtAuthenticationConverter nativeJwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            Object roles = jwt.getClaim("roles");
            if (roles instanceof Collection<?> list) {
                for (Object role : list) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return authorities;
        });
        return converter;
    }

    /**
     * The one JwtDecoder bean for all providers - replaces Spring Boot's autoconfigured
     * jwk-set-uri decoder, which validates only the signature and exp/nbf.
     *
     * <p>Keycloak mode additionally enforces the expected issuer and audience; without that,
     * any token signed by the same realm key (e.g. one issued to a different client sharing
     * the realm) would be accepted and silently provision its {@code preferred_username}.
     * The issuer is configured separately from the JWK endpoint because the {@code iss} claim
     * carries the URL the <em>browser</em> used to reach Keycloak, which in Docker setups
     * differs from the internal host the backend fetches the JWKS from - issuer-uri discovery
     * would therefore break, so we keep the jwk-set-uri decoder and validate explicitly.</p>
     */
    @Bean
    public JwtDecoder jwtDecoder(AuthProperties auth) {
        if (auth.isNative()) {
            return NimbusJwtDecoder.withSecretKey(nativeSecretKey(auth))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(keycloakTokenValidator(keycloakIssuer, keycloakAudience));
        return decoder;
    }

    /**
     * Spring's default JWT checks (timestamps + issuer) plus a validator requiring the
     * configured audience to be present in {@code aud}. Static so the composition is
     * unit-testable without a live Keycloak.
     */
    public static OAuth2TokenValidator<Jwt> keycloakTokenValidator(String issuer, String audience) {
        // Diamond operator here would NOT compile: JwtClaimValidator<T> is parameterized by the
        // claim's value type, not by Jwt, so the OAuth2TokenValidator<Jwt> target type gives javac
        // nothing to infer T from - it falls back to Object, and aud.contains(...) stops resolving.
        OAuth2TokenValidator<Jwt> hasAudience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, //NOSONAR
                aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), hasAudience);
    }

    // --- Native-provider beans (absent under keycloak/none) ---

    @Bean
    @ConditionalOnProperty(name = "planner.auth.provider", havingValue = "native")
    public JwtEncoder nativeJwtEncoder(AuthProperties auth) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(nativeSecretKey(auth)));
    }

    private static SecretKeySpec nativeSecretKey(AuthProperties auth) {
        String secret = auth.jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Native auth requires PLANNER_JWT_SECRET to be set to at least 32 bytes. "
                            + "Generate one with: openssl rand -base64 48");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
