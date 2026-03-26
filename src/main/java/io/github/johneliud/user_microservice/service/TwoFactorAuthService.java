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

    @Transactional
    public void verifyAndEnable(UUID userId, String totpCode) {
        log.info("POST /api/auth/2fa/verify - Verifying 2FA setup for userId: {}", userId);
        User user = findById(userId);

        if (user.getTotpSecret() == null) {
            log.warn("2FA verify failed - no secret set for userId: {}", userId);
            throw new IllegalStateException("2FA setup has not been initiated");
        }
        if (!totpUtil.verifyCode(user.getTotpSecret(), totpCode)) {
            log.warn("2FA verify failed - invalid code for userId: {}", userId);
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        log.info("2FA enabled for userId: {}", userId);
    }

    @Transactional
    public void disable(UUID userId, String password) {
        log.info("POST /api/auth/2fa/disable - Disabling 2FA for userId: {}", userId);
        User user = findById(userId);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("2FA disable failed - incorrect password for userId: {}", userId);
            throw new IllegalArgumentException("Invalid password");
        }

        user.setTwoFactorEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        log.info("2FA disabled for userId: {}", userId);
    }
}
