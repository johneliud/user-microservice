package io.github.johneliud.user_microservice.controller;

import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.LoginRequest;
import io.github.johneliud.user_microservice.dto.RefreshTokenRequest;
import io.github.johneliud.user_microservice.dto.RegisterRequest;
import io.github.johneliud.user_microservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register - Registration request received for email: {}", request.email());
        AuthResponse response = authService.register(request);
        log.info("POST /api/auth/register - Registration successful for email: {}", request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
