package io.github.johneliud.user_microservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WatchlistItemResponse(
        UUID movieId,
        LocalDateTime addedAt
) {}