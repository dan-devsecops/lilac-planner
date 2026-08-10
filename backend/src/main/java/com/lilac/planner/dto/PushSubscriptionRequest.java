package com.lilac.planner.dto;

import com.lilac.planner.domain.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotNull Platform platform,
        @NotBlank @Size(max = 2048) String token,
        @Size(max = 512) String p256dh,
        @Size(max = 512) String auth
) {}
