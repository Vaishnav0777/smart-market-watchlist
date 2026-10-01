package com.smartwatch.user.dto;

import com.smartwatch.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record CurrentUserResponse(UUID id, String email, String displayName, Instant createdAt) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
