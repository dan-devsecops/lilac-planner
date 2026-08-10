package com.lilac.planner.service;

import com.lilac.planner.dto.StatPointDto;
import com.lilac.planner.model.Task;
import com.lilac.planner.persistence.PlannerStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StatisticsService {

    private final PlannerStore store;

    public StatisticsService(PlannerStore store) {
        this.store = store;
    }

    public List<StatPointDto> range(String userId, LocalDate from, LocalDate to) {
        return store.findDaysInRange(userId, from, to).stream()
                .map(d -> new StatPointDto(
                        d.getDate(),
                        d.totalPoints(),
                        (int) d.getTasks().stream().filter(Task::isCompleted).count(),
                        d.getTasks().size()))
                .toList();
    }
}
