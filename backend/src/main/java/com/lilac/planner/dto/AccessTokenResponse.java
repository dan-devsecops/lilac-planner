package com.lilac.planner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * HTTP response body for login and refresh. By default {@code refreshToken} is {@code null} (and
 * therefore omitted from the JSON) - the refresh token is delivered as an HttpOnly cookie, not in
 * the body. Mobile clients that opted into body mode (see {@link LoginRequest#client()} and
 * {@link RefreshRequest}) get the rotated refresh token via {@link #withRefreshToken}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken) {
    public static AccessTokenResponse from(TokenResponse tr) {
        return new AccessTokenResponse(tr.accessToken(), tr.tokenType(), tr.expiresIn(), null);
    }

    public static AccessTokenResponse withRefreshToken(TokenResponse tr) {
        return new AccessTokenResponse(tr.accessToken(), tr.tokenType(), tr.expiresIn(), tr.refreshToken());
    }
}
