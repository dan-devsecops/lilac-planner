package com.lilac.planner.dto;

import com.lilac.planner.model.User;

import java.util.List;

/** Public view of a user - never exposes the password hash. */
public record UserDto(String id, String username, String displayName, String email, List<String> roles, String timezone) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRoles(), u.getTimezone());
    }
}
