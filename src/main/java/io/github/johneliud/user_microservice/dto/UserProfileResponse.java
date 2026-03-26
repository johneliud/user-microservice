package io.github.johneliud.user_microservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        boolean twoFactorEnabled,
        LocalDateTime createdAt
) {}
