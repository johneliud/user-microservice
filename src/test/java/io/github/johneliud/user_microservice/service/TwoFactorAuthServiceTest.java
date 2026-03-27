package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.TwoFactorAuthRequest;
import io.github.johneliud.user_microservice.dto.TwoFactorSetupResponse;
import io.github.johneliud.user_microservice.entity.Role;
import io.github.johneliud.user_microservice.entity.User;
import io.github.johneliud.user_microservice.repository.RefreshTokenRepository;
import io.github.johneliud.user_microservice.repository.UserRepository;
import io.github.johneliud.user_microservice.util.JwtUtil;
import io.github.johneliud.user_microservice.util.TotpUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private TotpUtil totpUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private TwoFactorAuthService twoFactorAuthService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_ID_STR = USER_ID.toString();
    private static final String TOTP_SECRET = "BASE32SECRET";
    private static final String VALID_CODE = "123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(twoFactorAuthService, "refreshExpiration", 604800000L);
    }

    private User buildUser(boolean twoFactorEnabled, String totpSecret) {
        return User.builder()
                .id(USER_ID)
                .username("johndoe")
                .email("john@example.com")
                .passwordHash("hashed")
                .roles(Set.of(Role.ROLE_USER))
                .twoFactorEnabled(twoFactorEnabled)
                .totpSecret(totpSecret)
                .build();
    }

    // ── setup ─────────────────────────────────────────────────────────────────

    @Test
    void setup_success_returnsSecretAndQrUri() {
        User user = buildUser(false, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totpUtil.generateSecret()).thenReturn(TOTP_SECRET);
        when(totpUtil.generateQrCodeUri(TOTP_SECRET, user.getEmail())).thenReturn("otpauth://totp/...");
        when(userRepository.save(any())).thenReturn(user);

        TwoFactorSetupResponse response = twoFactorAuthService.setup(USER_ID);

        assertEquals(TOTP_SECRET, response.secret());
        assertEquals("otpauth://totp/...", response.qrCodeUri());
        assertEquals(TOTP_SECRET, user.getTotpSecret());
        assertFalse(user.isTwoFactorEnabled());
    }

    // ── verifyAndEnable ───────────────────────────────────────────────────────

    @Test
    void verifyAndEnable_validCode_enables2FA() {
        User user = buildUser(false, TOTP_SECRET);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totpUtil.verifyCode(TOTP_SECRET, VALID_CODE)).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);

        twoFactorAuthService.verifyAndEnable(USER_ID, VALID_CODE);

        assertTrue(user.isTwoFactorEnabled());
    }

    @Test
    void verifyAndEnable_noSecret_throwsException() {
        User user = buildUser(false, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class,
                () -> twoFactorAuthService.verifyAndEnable(USER_ID, VALID_CODE));
    }

    @Test
    void verifyAndEnable_invalidCode_throwsException() {
        User user = buildUser(false, TOTP_SECRET);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totpUtil.verifyCode(TOTP_SECRET, "000000")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> twoFactorAuthService.verifyAndEnable(USER_ID, "000000"));
        assertFalse(user.isTwoFactorEnabled());
    }

    // ── disable ───────────────────────────────────────────────────────────────

    @Test
    void disable_correctPassword_disables2FA() {
        User user = buildUser(true, TOTP_SECRET);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret@123", "hashed")).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);

        twoFactorAuthService.disable(USER_ID, "Secret@123");

        assertFalse(user.isTwoFactorEnabled());
        assertNull(user.getTotpSecret());
    }

    @Test
    void disable_wrongPassword_throwsException() {
        User user = buildUser(true, TOTP_SECRET);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> twoFactorAuthService.disable(USER_ID, "wrong"));
        assertTrue(user.isTwoFactorEnabled());
    }

    // ── authenticate ──────────────────────────────────────────────────────────

    @Test
    void authenticate_validMfaTokenAndCode_returnsFullAuthResponse() {
        User user = buildUser(true, TOTP_SECRET);
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateMfaToken("mfa-token")).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(USER_ID_STR);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totpUtil.verifyCode(TOTP_SECRET, VALID_CODE)).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("access-token");
        when(jwtUtil.getExpiration()).thenReturn(86400000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = twoFactorAuthService.authenticate(
                new TwoFactorAuthRequest("mfa-token", VALID_CODE));

        assertEquals("access-token", response.accessToken());
    }

    @Test
    void authenticate_invalidMfaToken_throwsException() {
        when(jwtUtil.validateMfaToken("bad-token"))
                .thenThrow(new IllegalArgumentException("Not a valid MFA token"));

        assertThrows(IllegalArgumentException.class,
                () -> twoFactorAuthService.authenticate(new TwoFactorAuthRequest("bad-token", VALID_CODE)));
    }

    @Test
    void authenticate_invalidTotpCode_throwsException() {
        User user = buildUser(true, TOTP_SECRET);
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateMfaToken("mfa-token")).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(USER_ID_STR);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totpUtil.verifyCode(TOTP_SECRET, "000000")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> twoFactorAuthService.authenticate(new TwoFactorAuthRequest("mfa-token", "000000")));
    }

    @Test
    void authenticate_2faNotEnabled_throwsException() {
        User user = buildUser(false, null);
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateMfaToken("mfa-token")).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(USER_ID_STR);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class,
                () -> twoFactorAuthService.authenticate(new TwoFactorAuthRequest("mfa-token", VALID_CODE)));
    }
}