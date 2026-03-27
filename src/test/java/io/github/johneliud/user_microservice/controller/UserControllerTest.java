package io.github.johneliud.user_microservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.johneliud.user_microservice.dto.UpdateProfileRequest;
import io.github.johneliud.user_microservice.dto.UserProfileResponse;
import io.github.johneliud.user_microservice.exception.GlobalExceptionHandler;
import io.github.johneliud.user_microservice.service.UserService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserService userService;
    @InjectMocks private UserController userController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUserAuth(UUID userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MockHttpServletRequestBuilder withUserPrincipal(MockHttpServletRequestBuilder builder, UUID userId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
        return builder.principal(auth);
    }

    private UserProfileResponse stubProfile(UUID id, String username) {
        return new UserProfileResponse(id, username, "john@example.com", false, LocalDateTime.now());
    }

    // ── GET /api/users/profile ────────────────────────────────────────────────

    @Test
    void getProfile_authenticated_returns200WithProfile() throws Exception {
        when(userService.getProfile(USER_ID)).thenReturn(stubProfile(USER_ID, "johndoe"));

        mockMvc.perform(withUserPrincipal(get("/api/users/profile"), USER_ID, "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    // ── PUT /api/users/profile ────────────────────────────────────────────────

    @Test
    void updateProfile_newUsername_returns200() throws Exception {
        when(userService.updateProfile(eq(USER_ID), any())).thenReturn(stubProfile(USER_ID, "newname"));

        mockMvc.perform(withUserPrincipal(put("/api/users/profile"), USER_ID, "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("newname", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newname"));
    }

    @Test
    void updateProfile_usernameTaken_returns400() throws Exception {
        when(userService.updateProfile(eq(USER_ID), any()))
                .thenThrow(new IllegalArgumentException("Username is already taken"));

        mockMvc.perform(withUserPrincipal(put("/api/users/profile"), USER_ID, "ROLE_USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("taken", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Username is already taken"));
    }

    // ── DELETE /api/users/profile ─────────────────────────────────────────────

    @Test
    void deleteAccount_authenticated_returns204() throws Exception {
        mockMvc.perform(withUserPrincipal(delete("/api/users/profile"), USER_ID, "ROLE_USER"))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/users (admin) ────────────────────────────────────────────────

    @Test
    void getAllUsers_admin_returns200WithList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                stubProfile(USER_ID, "johndoe"),
                stubProfile(UUID.randomUUID(), "janedoe")));

        mockMvc.perform(withUserPrincipal(get("/api/users"), ADMIN_ID, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── DELETE /api/users/{id} (admin) ────────────────────────────────────────

    @Test
    void deleteUser_admin_returns204() throws Exception {
        mockMvc.perform(withUserPrincipal(delete("/api/users/{id}", USER_ID), ADMIN_ID, "ROLE_ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUser_notFound_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("User not found"))
                .when(userService).deleteUser(USER_ID);

        mockMvc.perform(withUserPrincipal(delete("/api/users/{id}", USER_ID), ADMIN_ID, "ROLE_ADMIN"))
                .andExpect(status().isBadRequest());
    }
}