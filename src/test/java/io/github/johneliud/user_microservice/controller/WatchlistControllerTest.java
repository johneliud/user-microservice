package io.github.johneliud.user_microservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.johneliud.user_microservice.dto.WatchlistItemResponse;
import io.github.johneliud.user_microservice.dto.WatchlistRequest;
import io.github.johneliud.user_microservice.exception.GlobalExceptionHandler;
import io.github.johneliud.user_microservice.service.WatchlistService;
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
class WatchlistControllerTest {

    @Mock private WatchlistService watchlistService;
    @InjectMocks private WatchlistController watchlistController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MOVIE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(watchlistController)
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

    private WatchlistItemResponse stubItem() {
        return new WatchlistItemResponse(MOVIE_ID, LocalDateTime.now());
    }

    // ── GET /api/users/profile/watchlist ──────────────────────────────────────

    @Test
    void getWatchlist_authenticated_returns200WithList() throws Exception {
        when(watchlistService.getWatchlist(USER_ID)).thenReturn(List.of(stubItem(), stubItem()));

        mockMvc.perform(withUserPrincipal(get("/api/users/profile/watchlist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getWatchlist_empty_returns200WithEmptyArray() throws Exception {
        when(watchlistService.getWatchlist(USER_ID)).thenReturn(List.of());

        mockMvc.perform(withUserPrincipal(get("/api/users/profile/watchlist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── POST /api/users/profile/watchlist ─────────────────────────────────────

    @Test
    void addMovie_validRequest_returns201WithItem() throws Exception {
        when(watchlistService.addMovie(eq(USER_ID), any())).thenReturn(stubItem());

        mockMvc.perform(withUserPrincipal(post("/api/users/profile/watchlist"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WatchlistRequest(MOVIE_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movieId").value(MOVIE_ID.toString()));
    }

    @Test
    void addMovie_nullMovieId_returns400() throws Exception {
        mockMvc.perform(withUserPrincipal(post("/api/users/profile/watchlist"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movieId\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMovie_duplicate_returns400() throws Exception {
        when(watchlistService.addMovie(eq(USER_ID), any()))
                .thenThrow(new IllegalArgumentException("Movie is already in your watchlist"));

        mockMvc.perform(withUserPrincipal(post("/api/users/profile/watchlist"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WatchlistRequest(MOVIE_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Movie is already in your watchlist"));
    }

    // ── DELETE /api/users/profile/watchlist/{movieId} ─────────────────────────

    @Test
    void removeMovie_exists_returns204() throws Exception {
        mockMvc.perform(withUserPrincipal(delete("/api/users/profile/watchlist/{movieId}", MOVIE_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeMovie_notFound_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Movie is not in your watchlist"))
                .when(watchlistService).removeMovie(eq(USER_ID), eq(MOVIE_ID));

        mockMvc.perform(withUserPrincipal(delete("/api/users/profile/watchlist/{movieId}", MOVIE_ID)))
                .andExpect(status().isBadRequest());
    }
}