package io.github.johneliud.user_microservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.johneliud.user_microservice.dto.*;
import io.github.johneliud.user_microservice.exception.GlobalExceptionHandler;
import io.github.johneliud.user_microservice.service.TwoFactorAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthControllerTest {

    @Mock private TwoFactorAuthService twoFactorAuthService;
    @InjectMocks private TwoFactorAuthController twoFactorAuthController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String VALID_CODE = "123456";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(twoFactorAuthController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequestBuilder withUserPrincipal(MockHttpServletRequestBuilder builder) {
        var auth = new UsernamePasswordAuthenticationToken(
                USER_ID.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return builder.principal(auth);
    }

    // ── POST /api/auth/2fa/setup ──────────────────────────────────────────────

    @Test
    void setup_authenticated_returns200WithSecretAndQrUri() throws Exception {
        when(twoFactorAuthService.setup(USER_ID))
                .thenReturn(new TwoFactorSetupResponse("BASE32SECRET", "otpauth://totp/..."));

        mockMvc.perform(withUserPrincipal(post("/api/auth/2fa/setup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("BASE32SECRET"))
                .andExpect(jsonPath("$.qrCodeUri").value("otpauth://totp/..."));
    }

    // ── POST /api/auth/2fa/verify ─────────────────────────────────────────────

    @Test
    void verify_validCode_returns204() throws Exception {
        mockMvc.perform(withUserPrincipal(post("/api/auth/2fa/verify"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorVerifyRequest(VALID_CODE))))
                .andExpect(status().isNoContent());
    }

    @Test
    void verify_invalidCodeFormat_returns400() throws Exception {
        mockMvc.perform(withUserPrincipal(post("/api/auth/2fa/verify"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorVerifyRequest("12345"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_wrongCode_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid TOTP code"))
                .when(twoFactorAuthService).verifyAndEnable(eq(USER_ID), eq(VALID_CODE));

        mockMvc.perform(withUserPrincipal(post("/api/auth/2fa/verify"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorVerifyRequest(VALID_CODE))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/2fa/disable ────────────────────────────────────────────

    @Test
    void disable_correctPassword_returns204() throws Exception {
        mockMvc.perform(withUserPrincipal(post("/api/auth/2fa/disable"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorDisableRequest("Secret@123"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void disable_wrongPassword_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Incorrect password"))
                .when(twoFactorAuthService).disable(eq(USER_ID), any());

        mockMvc.perform(withUserPrincipal(post("/api/auth/2fa/disable"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorDisableRequest("wrong"))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/2fa/authenticate (public) ──────────────────────────────

    @Test
    void authenticate_validMfaTokenAndCode_returns200() throws Exception {
        when(twoFactorAuthService.authenticate(any()))
                .thenReturn(new AuthResponse("access-token", "refresh-token", "Bearer", 86400000L));

        mockMvc.perform(post("/api/auth/2fa/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TwoFactorAuthRequest("mfa-token", VALID_CODE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void authenticate_invalidMfaToken_returns400() throws Exception {
        when(twoFactorAuthService.authenticate(any()))
                .thenThrow(new IllegalArgumentException("Not a valid MFA token"));

        mockMvc.perform(post("/api/auth/2fa/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TwoFactorAuthRequest("bad-token", VALID_CODE))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticate_invalidTotpFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/2fa/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TwoFactorAuthRequest("mfa-token", "12345"))))
                .andExpect(status().isBadRequest());
    }
}