package com.lilac.planner.controller;

import com.lilac.planner.dto.CreateUserRequest;
import com.lilac.planner.dto.UserDto;
import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.service.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final PlannerStore store;
    private final CurrentUserResolver resolver;

    public UserController(PlannerStore store, CurrentUserResolver resolver) {
        this.store = store;
        this.resolver = resolver;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> list() {
        return store.listUsers().stream().map(UserDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        String display = (req.displayName() == null || req.displayName().isBlank())
                ? req.username() : req.displayName();
        return UserDto.from(store.createUser(req.username(), display));
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return UserDto.from(resolver.resolve(jwt));
    }
}
