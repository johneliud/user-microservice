CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    totp_secret   VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);
