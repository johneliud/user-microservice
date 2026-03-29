package io.github.johneliud.user_microservice.controller;

import io.github.johneliud.user_microservice.dto.AccessTokenResponse;
import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.LoginRequest;
import io.github.johneliud.user_microservice.dto.RefreshTokenRequest;
import io.github.johneliud.user_microservice.dto.RegisterRequest;
import io.github.johneliud.user_microservice.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AccessTokenResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        log.info("POST /api/auth/register - Registration request received for email: {}", request.email());
        AuthResponse auth = authService.register(request);
        setRefreshCookie(response, auth.refreshToken());
        log.info("POST /api/auth/register - Registration successful for email: {}", request.email());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AccessTokenResponse.of(auth.accessToken(), auth.expiresIn()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("POST /api/auth/login - Login request received for username: {}", request.username());
        var result = authService.login(request);
        if (result instanceof AuthResponse auth) {
            setRefreshCookie(response, auth.refreshToken());
            log.info("POST /api/auth/login - Login successful for username: {}", request.username());
            return ResponseEntity.ok(AccessTokenResponse.of(auth.accessToken(), auth.expiresIn()));
        }
        // MfaRequiredResponse — no refresh cookie yet; client must complete 2FA first
        log.info("POST /api/auth/login - 2FA required for username: {}", request.username());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        log.info("POST /api/auth/refresh - Token refresh request received");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthResponse auth = authService.refresh(new RefreshTokenRequest(refreshToken));
        setRefreshCookie(response, auth.refreshToken());
        log.info("POST /api/auth/refresh - Token refreshed successfully");
        return ResponseEntity.ok(AccessTokenResponse.of(auth.accessToken(), auth.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        log.info("POST /api/auth/logout - Logout request received");
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(new RefreshTokenRequest(refreshToken));
        }
        clearRefreshCookie(response);
        log.info("POST /api/auth/logout - Logout successful");
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(refreshExpiration / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}