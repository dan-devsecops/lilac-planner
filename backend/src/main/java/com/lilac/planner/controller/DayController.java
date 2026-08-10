package com.lilac.planner.controller;

import com.lilac.planner.dto.DayDto;
import com.lilac.planner.service.CurrentUserResolver;
import com.lilac.planner.service.PlannerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/days")
public class DayController {

    private final PlannerService planner;
    private final CurrentUserResolver resolver;

    public DayController(PlannerService planner, CurrentUserResolver resolver) {
        this.planner = planner;
        this.resolver = resolver;
    }

    @GetMapping("/{date}")
    public DayDto getDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt) {
        DateWindow.require(date, "date");
        var user = resolver.resolve(jwt);
        return DayDto.from(planner.getOrCreateDay(user.getId(), date));
    }
}
