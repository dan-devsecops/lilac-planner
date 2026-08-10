package com.lilac.planner.config;

/** The authentication strategy the backend runs with. Mutually exclusive. */
public enum AuthProvider {
    /** No authentication - every request is permitted and resolves to a fixed "dev" user. */
    NONE,
    /** Keycloak SSO - validates Keycloak-issued JWTs as an OAuth2 resource server. */
    KEYCLOAK,
    /** Native username+password - validates our own HS256 JWTs, with /api/v1/auth/** endpoints. */
    NATIVE
}
