package io.github.johneliud.user_microservice.controller;

import io.github.johneliud.user_microservice.dto.WatchlistItemResponse;
import io.github.johneliud.user_microservice.dto.WatchlistRequest;
import io.github.johneliud.user_microservice.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users/profile/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ResponseEntity<List<WatchlistItemResponse>> getWatchlist(Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        log.info("GET /api/users/profile/watchlist - Request for userId: {}", userId);
        return ResponseEntity.ok(watchlistService.getWatchlist(userId));
    }

    @PostMapping
    public ResponseEntity<WatchlistItemResponse> addMovie(@Valid @RequestBody WatchlistRequest request,
                                                          Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        log.info("POST /api/users/profile/watchlist - Adding movieId: {} for userId: {}", request.movieId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(watchlistService.addMovie(userId, request));
    }
}
