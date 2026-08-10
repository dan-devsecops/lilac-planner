package com.lilac.planner.dto;

import com.lilac.planner.domain.Platform;
import com.lilac.planner.model.PushSubscription;

import java.time.Instant;

/** Public view of a registered device - never echoes back the raw token or Web Push keys. */
public record PushSubscriptionDto(String id, Platform platform, Instant createdAt, Instant lastSeenAt) {
    public static PushSubscriptionDto from(PushSubscription s) {
        return new PushSubscriptionDto(s.getId(), s.getPlatform(), s.getCreatedAt(), s.getLastSeenAt());
    }
}
