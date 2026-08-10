package com.lilac.planner.controller;

import com.lilac.planner.dto.DayDto;
import com.lilac.planner.dto.TaskRequest;
import com.lilac.planner.service.CurrentUserResolver;
import com.lilac.planner.service.PlannerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/days/{date}/tasks")
public class TaskController {

    private final PlannerService planner;
    private final CurrentUserResolver resolver;

    public TaskController(PlannerService planner, CurrentUserResolver resolver) {
        this.planner = planner;
        this.resolver = resolver;
    }

    @PostMapping
    public DayDto add(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @Valid @RequestBody TaskRequest req,
                      @AuthenticationPrincipal Jwt jwt) {
        // title stays a manual check: the same record serves PATCH, where a null title means
        // "leave unchanged", so @NotBlank on the field would reject valid partial updates.
        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }
        DateWindow.require(date, "date");
        var user = resolver.resolve(jwt);
        return DayDto.from(planner.addTask(user.getId(), date, req));
    }

    @PatchMapping("/{taskId}")
    public DayDto update(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @PathVariable String taskId,
                         @Valid @RequestBody TaskRequest req,
                         @AuthenticationPrincipal Jwt jwt) {
        DateWindow.require(date, "date");
        var user = resolver.resolve(jwt);
        return DayDto.from(planner.updateTask(user.getId(), date, taskId, req));
    }

    @DeleteMapping("/{taskId}")
    public DayDto delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @PathVariable String taskId,
                         @AuthenticationPrincipal Jwt jwt) {
        DateWindow.require(date, "date");
        var user = resolver.resolve(jwt);
        return DayDto.from(planner.deleteTask(user.getId(), date, taskId));
    }

    @PutMapping("/reorder")
    public DayDto reorder(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestBody @Size(max = 500) List<@Size(max = 64) String> orderedTaskIds,
                          @AuthenticationPrincipal Jwt jwt) {
        DateWindow.require(date, "date");
        var user = resolver.resolve(jwt);
        return DayDto.from(planner.reorderTasks(user.getId(), date, orderedTaskIds));
    }
}
