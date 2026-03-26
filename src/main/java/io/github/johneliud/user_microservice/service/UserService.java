package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.UpdateProfileRequest;
import io.github.johneliud.user_microservice.dto.UserProfileResponse;
import io.github.johneliud.user_microservice.entity.User;
import io.github.johneliud.user_microservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getProfile(UUID userId) {
        log.debug("Fetching profile for userId: {}", userId);
        User user = findById(userId);
        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        log.debug("Updating profile for userId: {}", userId);
        User user = findById(userId);

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                log.warn("Profile update failed - username already taken: {}", request.username());
                throw new IllegalArgumentException("Username already taken");
            }
            user.setUsername(request.username());
            log.info("Username updated for userId: {}", userId);
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                log.warn("Profile update failed - email already registered: {}", request.email());
                throw new IllegalArgumentException("Email already registered");
            }
            user.setEmail(request.email());
            log.info("Email updated for userId: {}", userId);
        }

        return toProfileResponse(userRepository.save(user));
    }
}
