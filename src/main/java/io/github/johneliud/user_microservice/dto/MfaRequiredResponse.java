package io.github.johneliud.user_microservice.dto;

public record MfaRequiredResponse(
        boolean requires2fa,
        String mfaToken
) implements LoginResponse {}
