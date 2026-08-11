package com.training.platform.users.dto;

import com.training.platform.users.model.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        UserStatus status,
        UserProfileResponse profile,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
