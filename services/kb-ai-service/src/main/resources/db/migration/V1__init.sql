-- ai schema: sessions and messages
CREATE SCHEMA IF NOT EXISTS ai;

CREATE TABLE ai.sessions (
    id         VARCHAR(64)  PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    title      VARCHAR(255) NOT NULL DEFAULT 'New Chat',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX sessions_user_updated_idx ON ai.sessions (user_id, updated_at DESC);

CREATE TABLE ai.messages (
    id         BIGSERIAL    PRIMARY KEY,
    session_id VARCHAR(64)  NOT NULL REFERENCES ai.sessions(id) ON DELETE CASCADE,
    sender     VARCHAR(16)  NOT NULL,   -- 'USER' | 'ASSISTANT'
    content    TEXT         NOT NULL,
    model      VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX messages_session_created_idx ON ai.messages (session_id, created_at ASC);
