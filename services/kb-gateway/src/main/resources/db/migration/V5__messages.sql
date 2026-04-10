CREATE TABLE user_message (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  subject     VARCHAR(200) NOT NULL,
  content     TEXT NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  reply       TEXT,
  replied_by  UUID REFERENCES app_user(id),
  replied_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON user_message (user_id);
CREATE INDEX ON user_message (status);
