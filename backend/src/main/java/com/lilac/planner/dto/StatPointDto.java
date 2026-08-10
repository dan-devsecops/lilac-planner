package com.lilac.planner.dto;

import java.time.LocalDate;

public record StatPointDto(LocalDate date, int points, int completedTasks, int totalTasks) {}
