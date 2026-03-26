package io.github.johneliud.user_microservice.dto;

public record TwoFactorSetupResponse(
        String secret,
        String qrCodeUri
) {}
