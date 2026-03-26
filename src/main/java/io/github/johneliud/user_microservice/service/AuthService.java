package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.LoginRequest;
import io.github.johneliud.user_microservice.dto.RefreshTokenRequest;
import io.github.johneliud.user_microservice.dto.RegisterRequest;
import io.github.johneliud.user_microservice.entity.RefreshToken;
import io.github.johneliud.user_microservice.entity.Role;
import io.github.johneliud.user_microservice.entity.User;
import io.github.johneliud.user_microservice.repository.RefreshTokenRepository;
import io.github.johneliud.user_microservice.repository.UserRepository;
import io.github.johneliud.user_microservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.debug("Checking uniqueness constraints for username: {} and email: {}", request.username(), request.email());

        if (userRepository.existsByUsername(request.username())) {
            log.warn("Registration failed - username already taken: {}", request.username());
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed - email already registered: {}", request.email());
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(Set.of(Role.ROLE_USER))
                .build();

        userRepository.save(user);
        log.info("New user registered - id: {}, username: {}", user.getId(), user.getUsername());

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = createRefreshToken(user.getId());

        return AuthResponse.of(accessToken, refreshToken, jwtUtil.getExpiration());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.debug("Authenticating user: {}", request.username());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow();

        log.info("User authenticated - id: {}, username: {}", user.getId(), user.getUsername());

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = createRefreshToken(user.getId());

        return AuthResponse.of(accessToken, refreshToken, jwtUtil.getExpiration());
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        log.debug("Processing refresh token rotation");

        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> {
                    log.warn("Refresh failed - token not found");
                    return new IllegalArgumentException("Invalid refresh token");
                });

        if (stored.isRevoked()) {
            log.warn("Refresh failed - token already revoked for userId: {}", stored.getUserId());
            throw new IllegalArgumentException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh failed - token expired for userId: {}", stored.getUserId());
            throw new IllegalArgumentException("Refresh token has expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow();

        log.info("Refresh token rotated for userId: {}", user.getId());

        String accessToken = jwtUtil.generateToken(user);
        String newRefreshToken = createRefreshToken(user.getId());

        return AuthResponse.of(accessToken, newRefreshToken, jwtUtil.getExpiration());
    }
}
