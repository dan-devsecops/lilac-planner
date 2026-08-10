package com.lilac.planner.controller;

import com.lilac.planner.dto.VapidPublicKeyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated endpoint so a web client can subscribe via {@code PushManager}
 * before the user has (or without ever needing) an access token. Returns an empty key,
 * not a 500, when VAPID hasn't been configured - see {@code SecurityConfig} for how this
 * path is permitted alongside {@code /api/v1/meta}.
 */
@RestController
@RequestMapping("/api/v1/push")
public class PushConfigController {

    private final String vapidPublicKey;

    public PushConfigController(@Value("${planner.push.vapid.public-key:}") String vapidPublicKey) {
        this.vapidPublicKey = vapidPublicKey;
    }

    @GetMapping("/vapid-public-key")
    public VapidPublicKeyResponse get() {
        return new VapidPublicKeyResponse(vapidPublicKey);
    }
}
