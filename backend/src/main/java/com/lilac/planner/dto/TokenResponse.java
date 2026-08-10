package com.lilac.planner.dto;

/** Issued on login and refresh. {@code expiresIn} is the access-token lifetime in seconds. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
