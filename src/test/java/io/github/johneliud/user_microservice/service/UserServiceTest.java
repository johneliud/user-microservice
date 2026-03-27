package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.UpdateProfileRequest;
import io.github.johneliud.user_microservice.dto.UserProfileResponse;
import io.github.johneliud.user_microservice.entity.Role;
import io.github.johneliud.user_microservice.entity.User;
import io.github.johneliud.user_microservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    private static final UUID USER_ID = UUID.randomUUID();

    private User buildUser() {
        return User.builder()
                .id(USER_ID)
                .username("johndoe")
                .email("john@example.com")
                .passwordHash("hashed")
                .roles(Set.of(Role.ROLE_USER))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void getProfile_exists_returnsResponse() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(buildUser()));

        UserProfileResponse response = userService.getProfile(USER_ID);

        assertEquals(USER_ID, response.id());
        assertEquals("johndoe", response.username());
    }

    @Test
    void getProfile_notFound_throwsException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.getProfile(USER_ID));
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_newUsername_updatesSuccessfully() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newname")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse response = userService.updateProfile(USER_ID,
                new UpdateProfileRequest("newname", null));

        assertEquals("newname", response.username());
    }

    @Test
    void updateProfile_usernameTaken_throwsException() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(USER_ID, new UpdateProfileRequest("taken", null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_newEmail_updatesSuccessfully() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse response = userService.updateProfile(USER_ID,
                new UpdateProfileRequest(null, "new@example.com"));

        assertEquals("new@example.com", response.email());
    }

    @Test
    void updateProfile_emailTaken_throwsException() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(USER_ID, new UpdateProfileRequest(null, "taken@example.com")));
    }

    @Test
    void updateProfile_sameUsernameAndEmail_noUpdateOccurs() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(USER_ID, new UpdateProfileRequest("johndoe", "john@example.com"));

        verify(userRepository, never()).existsByUsername(any());
        verify(userRepository, never()).existsByEmail(any());
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    void deleteAccount_exists_deletesUser() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.deleteAccount(USER_ID);

        verify(userRepository).delete(user);
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsMappedList() {
        when(userRepository.findAll()).thenReturn(List.of(buildUser(), buildUser()));

        List<UserProfileResponse> users = userService.getAllUsers();

        assertEquals(2, users.size());
    }

    // ── deleteUser (admin) ────────────────────────────────────────────────────

    @Test
    void deleteUser_exists_deletesUser() {
        User user = buildUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.deleteUser(USER_ID);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_notFound_throwsException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(USER_ID));
    }
}