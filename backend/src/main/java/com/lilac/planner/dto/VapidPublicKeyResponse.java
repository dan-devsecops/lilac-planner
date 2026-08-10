package com.lilac.planner.dto;

/** Response body for the public {@code GET /api/v1/push/vapid-public-key} endpoint. */
public record VapidPublicKeyResponse(String publicKey) {}
