package io.github.johneliud.user_microservice.controller;

import io.github.johneliud.user_microservice.dto.AccessTokenResponse;
import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.TwoFactorAuthRequest;
import io.github.johneliud.user_microservice.dto.TwoFactorDisableRequest;
import io.github.johneliud.user_microservice.dto.TwoFactorSetupResponse;
import io.github.johneliud.user_microservice.dto.TwoFactorVerifyRequest;
import io.github.johneliud.user_microservice.service.TwoFactorAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final TwoFactorAuthService twoFactorAuthService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

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
    public ResponseEntity<AccessTokenResponse> authenticate(
            @Valid @RequestBody TwoFactorAuthRequest request, HttpServletResponse response) {
        AuthResponse auth = twoFactorAuthService.authenticate(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, auth.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(refreshExpiration / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(AccessTokenResponse.of(auth.accessToken(), auth.expiresIn()));
    }
}