package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.TwoFactorAuthRequest;
import io.github.johneliud.user_microservice.dto.TwoFactorSetupResponse;
import io.github.johneliud.user_microservice.entity.RefreshToken;
import io.github.johneliud.user_microservice.entity.User;
import io.github.johneliud.user_microservice.repository.RefreshTokenRepository;
import io.github.johneliud.user_microservice.repository.UserRepository;
import io.github.johneliud.user_microservice.util.JwtUtil;
import io.github.johneliud.user_microservice.util.TotpUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final TotpUtil totpUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public TwoFactorSetupResponse setup(UUID userId) {
        log.info("POST /api/auth/2fa/setup - Setting up 2FA for userId: {}", userId);
        User user = findById(userId);

        String secret = totpUtil.generateSecret();
        String qrCodeUri = totpUtil.generateQrCodeUri(secret, user.getEmail());

        user.setTotpSecret(secret);
        userRepository.save(user);

        log.info("2FA secret generated for userId: {}", userId);
        return new TwoFactorSetupResponse(secret, qrCodeUri);
    }
}
