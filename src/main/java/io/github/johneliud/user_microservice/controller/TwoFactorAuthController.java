package io.github.johneliud.user_microservice.controller;

import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.TwoFactorAuthRequest;
import io.github.johneliud.user_microservice.dto.TwoFactorDisableRequest;
import io.github.johneliud.user_microservice.dto.TwoFactorSetupResponse;
import io.github.johneliud.user_microservice.dto.TwoFactorVerifyRequest;
import io.github.johneliud.user_microservice.service.TwoFactorAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;

    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> setup(Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(twoFactorAuthService.setup(userId));
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody TwoFactorVerifyRequest request,
                                       Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        twoFactorAuthService.verifyAndEnable(userId, request.totpCode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody TwoFactorDisableRequest request,
                                        Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        twoFactorAuthService.disable(userId, request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody TwoFactorAuthRequest request) {
        return ResponseEntity.ok(twoFactorAuthService.authenticate(request));
    }
}
