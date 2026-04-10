-- Initial schema for gateway (users / kb ownership / providers / models)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS app_user (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username      VARCHAR(64)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(16)  NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  display_name  VARCHAR(64),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS kb_user_kb (
  user_id    UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  kb_id      VARCHAR(64) NOT NULL,
  is_default BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kb_id)
);

CREATE TABLE IF NOT EXISTS kb_kb_settings (
  kb_id          VARCHAR(64) PRIMARY KEY,
  document_count INTEGER     NOT NULL DEFAULT 6,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ai_provider (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(64)  NOT NULL,
  base_url    VARCHAR(240) NOT NULL,
  api_key_enc TEXT         NOT NULL,
  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ai_model (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_id  UUID         NOT NULL REFERENCES ai_provider(id) ON DELETE CASCADE,
  model_id     VARCHAR(120) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (provider_id, model_id)
);

