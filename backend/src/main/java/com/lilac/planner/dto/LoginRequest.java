package com.lilac.planner.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code login} accepts either the username or the email address.
 *
 * <p>{@code client} is an optional opt-in flag - set it to {@code "mobile"} to have the response
 * include the rotated refresh token in the JSON body (in addition to the usual cookie). Omit it
 * (or send anything else) for the default web behavior, where the refresh token is never present
 * in the body.</p>
 */
public record LoginRequest(
        @NotBlank String login,
        @NotBlank String password,
        String client
) {}
