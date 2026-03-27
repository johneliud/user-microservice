package io.github.johneliud.user_microservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WatchlistRequest(

        @NotNull(message = "Movie ID is required")
        UUID movieId
) {}
