package io.github.johneliud.user_microservice.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorDisableRequest(

        @NotBlank(message = "Password is required")
        String password
) {}
