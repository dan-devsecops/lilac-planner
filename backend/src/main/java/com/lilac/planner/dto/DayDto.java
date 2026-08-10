package com.lilac.planner.dto;

import com.lilac.planner.model.Day;

import java.time.LocalDate;
import java.util.List;

public record DayDto(
        String id,
        String userId,
        LocalDate date,
        int totalPoints,
        int totalAvailablePoints,
        List<TaskDto> tasks,
        List<String> earnedStickers
) {
    public static DayDto from(Day d) {
        return new DayDto(
                d.getId(),
                d.getUserId(),
                d.getDate(),
                d.totalPoints(),
                d.totalAvailablePoints(),
                d.getTasks().stream()
                        .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                        .map(TaskDto::from)
                        .toList(),
                List.copyOf(d.getEarnedStickers())
        );
    }
}
