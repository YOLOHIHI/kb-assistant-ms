ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS avatar_data_url TEXT;
