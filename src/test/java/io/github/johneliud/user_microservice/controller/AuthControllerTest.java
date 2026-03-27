package io.github.johneliud.user_microservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.johneliud.user_microservice.dto.*;
import io.github.johneliud.user_microservice.exception.GlobalExceptionHandler;
import io.github.johneliud.user_microservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @InjectMocks private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VALID_PASSWORD = "Secret@123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuthResponse stubAuthResponse() {
        return new AuthResponse("access-token", "refresh-token", "Bearer", 86400000L);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_validRequest_returns201WithTokens() throws Exception {
        when(authService.register(any())).thenReturn(stubAuthResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("johndoe", "john@example.com", VALID_PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("", "john@example.com", VALID_PASSWORD))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("johndoe", "not-an-email", VALID_PASSWORD))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_weakPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("johndoe", "john@example.com", "weakpass"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_usernameTaken_returns400() throws Exception {
        when(authService.register(any())).thenThrow(new IllegalArgumentException("Username is already taken"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("johndoe", "john@example.com", VALID_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Username is already taken"));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_no2fa_returns200WithAuthResponse() throws Exception {
        when(authService.login(any())).thenReturn(stubAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("johndoe", VALID_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void login_2faEnabled_returns200WithMfaRequiredResponse() throws Exception {
        when(authService.login(any())).thenReturn(new MfaRequiredResponse(true, "mfa-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("johndoe", VALID_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2fa").value(true))
                .andExpect(jsonPath("$.mfaToken").value("mfa-token"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("johndoe", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_validToken_returns200() throws Exception {
        when(authService.refresh(any())).thenReturn(stubAuthResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refresh_invalidToken_returns400() throws Exception {
        when(authService.refresh(any())).thenThrow(new IllegalArgumentException("Invalid or expired refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("bad-token"))))
                .andExpect(status().isBadRequest());
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_validToken_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isNoContent());
    }
}