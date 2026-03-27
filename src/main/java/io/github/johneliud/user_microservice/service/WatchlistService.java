package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.WatchlistItemResponse;
import io.github.johneliud.user_microservice.dto.WatchlistRequest;
import io.github.johneliud.user_microservice.entity.Watchlist;
import io.github.johneliud.user_microservice.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;

    public List<WatchlistItemResponse> getWatchlist(UUID userId) {
        log.debug("Fetching watchlist for userId: {}", userId);
        return watchlistRepository.findByUserId(userId).stream()
                .map(w -> new WatchlistItemResponse(w.getMovieId(), w.getAddedAt()))
                .toList();
    }

    @Transactional
    public WatchlistItemResponse addMovie(UUID userId, WatchlistRequest request) {
        log.debug("Adding movieId: {} to watchlist for userId: {}", request.movieId(), userId);

        // TODO: validate movieId exists by calling movie-service once it is available
        if (watchlistRepository.existsByUserIdAndMovieId(userId, request.movieId())) {
            log.warn("Movie already in watchlist - userId: {}, movieId: {}", userId, request.movieId());
            throw new IllegalArgumentException("Movie is already in your watchlist");
        }

        Watchlist entry = Watchlist.builder()
                .userId(userId)
                .movieId(request.movieId())
                .build();

        Watchlist saved = watchlistRepository.save(entry);
        log.info("Movie added to watchlist - userId: {}, movieId: {}", userId, request.movieId());
        return new WatchlistItemResponse(saved.getMovieId(), saved.getAddedAt());
    }
}
