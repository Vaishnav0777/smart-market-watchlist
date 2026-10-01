-- Authentication columns and refresh sessions.
-- Flyway owns this change. V1 is left untouched.
-- password_hash stores a BCrypt hash, never a plaintext password.
-- token_hash stores a SHA-256 digest of an opaque refresh token, never the token itself.
-- No demo users are inserted.

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100) NOT NULL,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX uk_users_email_normalized ON users (lower(email));

CREATE TABLE refresh_sessions (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_refresh_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_sessions_user_id ON refresh_sessions (user_id);
CREATE INDEX idx_refresh_sessions_expires_at ON refresh_sessions (expires_at);
