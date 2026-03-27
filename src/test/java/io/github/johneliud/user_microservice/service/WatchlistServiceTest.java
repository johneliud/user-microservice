package io.github.johneliud.user_microservice.service;

import io.github.johneliud.user_microservice.dto.WatchlistItemResponse;
import io.github.johneliud.user_microservice.dto.WatchlistRequest;
import io.github.johneliud.user_microservice.entity.Watchlist;
import io.github.johneliud.user_microservice.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistRepository watchlistRepository;
    @InjectMocks private WatchlistService watchlistService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MOVIE_ID = UUID.randomUUID();

    private Watchlist buildEntry() {
        return Watchlist.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .movieId(MOVIE_ID)
                .addedAt(LocalDateTime.now())
                .build();
    }

    // ── getWatchlist ──────────────────────────────────────────────────────────

    @Test
    void getWatchlist_returnsMappedList() {
        when(watchlistRepository.findByUserId(USER_ID)).thenReturn(List.of(buildEntry(), buildEntry()));

        List<WatchlistItemResponse> result = watchlistService.getWatchlist(USER_ID);

        assertEquals(2, result.size());
        assertEquals(MOVIE_ID, result.get(0).movieId());
    }

    @Test
    void getWatchlist_empty_returnsEmptyList() {
        when(watchlistRepository.findByUserId(USER_ID)).thenReturn(List.of());

        assertTrue(watchlistService.getWatchlist(USER_ID).isEmpty());
    }

    // ── addMovie ──────────────────────────────────────────────────────────────

    @Test
    void addMovie_newEntry_savesAndReturnsResponse() {
        WatchlistRequest request = new WatchlistRequest(MOVIE_ID);
        Watchlist saved = buildEntry();

        when(watchlistRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(false);
        when(watchlistRepository.save(any())).thenReturn(saved);

        WatchlistItemResponse response = watchlistService.addMovie(USER_ID, request);

        assertEquals(MOVIE_ID, response.movieId());
        verify(watchlistRepository).save(any(Watchlist.class));
    }

    @Test
    void addMovie_duplicate_throwsException() {
        when(watchlistRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.addMovie(USER_ID, new WatchlistRequest(MOVIE_ID)));
        verify(watchlistRepository, never()).save(any());
    }

    // ── removeMovie ───────────────────────────────────────────────────────────

    @Test
    void removeMovie_exists_deletesEntry() {
        when(watchlistRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(true);

        watchlistService.removeMovie(USER_ID, MOVIE_ID);

        verify(watchlistRepository).deleteByUserIdAndMovieId(USER_ID, MOVIE_ID);
    }

    @Test
    void removeMovie_notFound_throwsException() {
        when(watchlistRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.removeMovie(USER_ID, MOVIE_ID));
        verify(watchlistRepository, never()).deleteByUserIdAndMovieId(any(), any());
    }
}