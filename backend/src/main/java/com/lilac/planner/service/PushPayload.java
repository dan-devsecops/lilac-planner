package com.lilac.planner.service;

import java.util.Map;

/**
 * Notification content shared by both push channels. Serialized as JSON for the Web Push
 * encrypted body (browser service worker parses {@code title}/{@code body}/{@code data}) and
 * mapped onto the Expo push message fields of the same name.
 */
public record PushPayload(String title, String body, Map<String, String> data) {

    public PushPayload {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public PushPayload(String title, String body) {
        this(title, body, Map.of());
    }
}
