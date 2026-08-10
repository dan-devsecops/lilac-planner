package com.lilac.planner.controller;

import com.lilac.planner.dto.StatPointDto;
import com.lilac.planner.service.CurrentUserResolver;
import com.lilac.planner.service.StatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    private final StatisticsService stats;
    private final CurrentUserResolver resolver;

    public StatisticsController(StatisticsService stats, CurrentUserResolver resolver) {
        this.stats = stats;
        this.resolver = resolver;
    }

    @GetMapping
    public List<StatPointDto> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        DateWindow.requireRange(from, to);
        var user = resolver.resolve(jwt);
        return stats.range(user.getId(), from, to);
    }
}
