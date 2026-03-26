package io.github.johneliud.user_microservice.controller;

import io.github.johneliud.user_microservice.dto.UpdateProfileRequest;
import io.github.johneliud.user_microservice.dto.UserProfileResponse;
import io.github.johneliud.user_microservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        log.info("GET /api/users/profile - Profile request for userId: {}", authentication.getPrincipal());
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                             Authentication authentication) {
        log.info("PUT /api/users/profile - Update request for userId: {}", authentication.getPrincipal());
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @DeleteMapping("/profile")
    public ResponseEntity<Void> deleteAccount(Authentication authentication) {
        log.info("DELETE /api/users/profile - Delete account request for userId: {}", authentication.getPrincipal());
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        userService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<UserProfileResponse>> getAllUsers() {
        log.info("GET /api/users - Admin fetching all users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        log.info("DELETE /api/users/{} - Admin deleting user", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
