package com.lilac.planner.domain;

/**
 * Role names stored on a {@code User} and emitted in native JWTs (mapped to {@code ROLE_*}
 * authorities by the security layer). Kept as plain strings so every storage backend can
 * persist them uniformly.
 */
public final class Role {
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

    private Role() {}
}
