package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.AuthResponse;
import io.github.johneliud.user_microservice.dto.LoginRequest;
import io.github.johneliud.user_microservice.dto.LoginResponse;
import io.github.johneliud.user_microservice.dto.MfaRequiredResponse;
import io.github.johneliud.user_microservice.dto.RegisterRequest;
import io.github.johneliud.user_microservice.entity.RefreshToken;
import io.github.johneliud.user_microservice.entity.Role;
import io.github.johneliud.user_microservice.entity.User;
import io.github.johneliud.user_microservice.repository.RefreshTokenRepository;
import io.github.johneliud.user_microservice.repository.UserRepository;
import io.github.johneliud.user_microservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthService authService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USERNAME = "johndoe";
    private static final String EMAIL = "john@example.com";
    private static final String PASSWORD = "Secret@123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpiration", 604800000L);
    }

    private User buildUser(boolean twoFactorEnabled) {
        return User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash("hashed")
                .roles(Set.of(Role.ROLE_USER))
                .twoFactorEnabled(twoFactorEnabled)
                .build();
    }

    private RefreshToken buildRefreshToken(boolean revoked, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("refresh-token")
                .userId(USER_ID)
                .revoked(revoked)
                .expiresAt(expiresAt)
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);

        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", USER_ID);
            return u;
        });
        when(jwtUtil.generateToken(any())).thenReturn("access-token");
        when(jwtUtil.getExpiration()).thenReturn(86400000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.register(request);

        assertInstanceOf(AuthResponse.class, response);
        assertEquals("access-token", ((AuthResponse) response).accessToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_usernameTaken_throwsException() {
        RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
        when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailTaken_throwsException() {
        RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
        when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_2faDisabled_returnsAuthResponse() {
        LoginRequest request = new LoginRequest(USERNAME, PASSWORD);
        User user = buildUser(false);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("access-token");
        when(jwtUtil.getExpiration()).thenReturn(86400000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.login(request);

        assertInstanceOf(AuthResponse.class, response);
        assertEquals("access-token", ((AuthResponse) response).accessToken());
    }

    @Test
    void login_2faEnabled_returnsMfaRequiredResponse() {
        LoginRequest request = new LoginRequest(USERNAME, PASSWORD);
        User user = buildUser(true);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(jwtUtil.generateMfaToken(user)).thenReturn("mfa-token");

        LoginResponse response = authService.login(request);

        assertInstanceOf(MfaRequiredResponse.class, response);
        assertTrue(((MfaRequiredResponse) response).requires2fa());
        assertEquals("mfa-token", ((MfaRequiredResponse) response).mfaToken());
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void login_badCredentials_throwsException() {
        LoginRequest request = new LoginRequest(USERNAME, "wrong");
        doThrow(BadCredentialsException.class)
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_validToken_returnsNewTokens() {
        RefreshToken stored = buildRefreshToken(false, LocalDateTime.now().plusDays(7));
        User user = buildUser(false);

        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(stored));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("new-access-token");
        when(jwtUtil.getExpiration()).thenReturn(86400000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh("refresh-token");

        assertEquals("new-access-token", response.accessToken());
        assertTrue(stored.isRevoked());
    }

    @Test
    void refresh_revokedToken_throwsException() {
        RefreshToken stored = buildRefreshToken(true, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(stored));

        assertThrows(IllegalArgumentException.class,
                () -> authService.refresh("refresh-token"));
    }

    @Test
    void refresh_expiredToken_throwsException() {
        RefreshToken stored = buildRefreshToken(false, LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(stored));

        assertThrows(IllegalArgumentException.class,
                () -> authService.refresh("refresh-token"));
    }

    @Test
    void refresh_unknownToken_throwsException() {
        when(refreshTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.refresh("bad-token"));
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_revokesToken() {
        RefreshToken stored = buildRefreshToken(false, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.logout("refresh-token");

        assertTrue(stored.isRevoked());
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void logout_unknownToken_noExceptionThrown() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.logout("unknown"));
        verify(refreshTokenRepository, never()).save(any());
    }
}