CREATE TABLE watchlist (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    movie_id   UUID      NOT NULL,
    added_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_watchlist_user_movie UNIQUE (user_id, movie_id)
);

CREATE INDEX idx_watchlist_user_id ON watchlist (user_id);
