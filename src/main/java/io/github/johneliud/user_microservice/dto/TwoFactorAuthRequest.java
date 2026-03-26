package io.github.johneliud.user_microservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TwoFactorAuthRequest(

        @NotBlank(message = "MFA token is required")
        String mfaToken,

        @NotBlank(message = "TOTP code is required")
        @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be exactly 6 digits")
        String totpCode
) {}
