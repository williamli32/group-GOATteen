-- Authentication sessions
-- Supports time-limited and revocable refresh sessions.

CREATE TABLE auth_sessions (

    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    refresh_token_hash VARCHAR(255) NOT NULL UNIQUE,

    expires_at TIMESTAMP NOT NULL,

    revoked_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    last_used_at TIMESTAMP,

    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT chk_auth_sessions_expiry
        CHECK (expires_at > created_at)
);


CREATE INDEX idx_auth_sessions_user_id
    ON auth_sessions(user_id);

CREATE INDEX idx_auth_sessions_expires_at
    ON auth_sessions(expires_at);