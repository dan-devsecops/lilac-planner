package com.lilac.planner.dto;

import com.lilac.planner.domain.Recurrence;
import com.lilac.planner.model.Task;

import java.time.LocalTime;

public record TaskDto(
        String id,
        String title,
        int points,
        boolean completed,
        int position,
        LocalTime scheduledTime,
        Recurrence recurrence,
        String recurrenceGroupId
) {
    public static TaskDto from(Task t) {
        return new TaskDto(
                t.getId(),
                t.getTitle(),
                t.getPoints(),
                t.isCompleted(),
                t.getPosition(),
                t.getScheduledTime(),
                t.getRecurrence(),
                t.getRecurrenceGroupId()
        );
    }
}
