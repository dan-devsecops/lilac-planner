package com.lilac.planner.unit;

import com.lilac.planner.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keycloak-mode JwtDecoder must reject any token that is merely signed by the realm
 * key: without issuer + audience checks, a token minted by another realm/deployment (wrong
 * {@code iss}) or issued to a different client of the same realm (wrong {@code aud}) would
 * authenticate and silently provision its {@code preferred_username}.
 */
@DisplayName("Keycloak token validator - issuer + audience enforcement")
class KeycloakTokenValidatorUnitTest {

    private static final String ISSUER = "http://localhost:8080/realms/lilac-planner";
    private static final String AUDIENCE = "lilac-planner-app";

    private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.keycloakTokenValidator(ISSUER, AUDIENCE);

    /** A structurally valid token; tests override the claims under attack. */
    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice")
                .claim("preferred_username", "alice")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    @DisplayName("correct issuer + audience → valid")
    void correctIssuerAndAudience_isValid() {
        Jwt jwt = token().issuer(ISSUER).audience(List.of(AUDIENCE)).build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("audience among several (Keycloak also adds 'account') → valid")
    void audienceAmongOthers_isValid() {
        Jwt jwt = token().issuer(ISSUER).audience(List.of("account", AUDIENCE)).build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("wrong issuer → rejected")
    void wrongIssuer_isRejected() {
        Jwt jwt = token().issuer("http://elsewhere:8080/realms/other").audience(List.of(AUDIENCE)).build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("missing audience (e.g. realm without the audience mapper) → rejected")
    void missingAudience_isRejected() {
        Jwt jwt = token().issuer(ISSUER).build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("token issued to a different client of the same realm → rejected")
    void wrongAudience_isRejected() {
        Jwt jwt = token().issuer(ISSUER).audience(List.of("other-client", "account")).build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("expired token still fails the default timestamp checks")
    void expiredToken_isRejected() {
        Jwt jwt = token()
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(300))
                .issuer(ISSUER).audience(List.of(AUDIENCE))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
}
