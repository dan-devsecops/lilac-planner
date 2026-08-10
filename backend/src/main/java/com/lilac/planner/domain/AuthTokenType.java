package com.lilac.planner.domain;

/** Kind of opaque token persisted (hashed) for native auth. */
public enum AuthTokenType {
    /** Long-lived, revocable token exchanged for a fresh access token. */
    REFRESH,
    /** Single-use, short-lived token emailed for the forgot-password flow. */
    PASSWORD_RESET
}
