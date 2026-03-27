package io.github.johneliud.user_microservice.repository;

import io.github.johneliud.user_microservice.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {

    List<Watchlist> findByUserId(UUID userId);

    boolean existsByUserIdAndMovieId(UUID userId, UUID movieId);

    void deleteByUserIdAndMovieId(UUID userId, UUID movieId);
}
