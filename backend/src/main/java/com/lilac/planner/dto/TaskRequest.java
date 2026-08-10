package com.lilac.planner.dto;

import com.lilac.planner.domain.Recurrence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record TaskRequest(
        @Size(max = 240) String title,
        @PositiveOrZero @Max(10_000) Integer points,
        Boolean completed,
        @PositiveOrZero Integer position,
        LocalTime scheduledTime,
        Boolean clearScheduledTime,
        Recurrence recurrence
) {}
