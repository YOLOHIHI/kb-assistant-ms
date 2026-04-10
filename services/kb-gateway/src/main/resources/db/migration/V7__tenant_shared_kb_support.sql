ALTER TABLE kb_kb_settings
  ADD CONSTRAINT ck_kb_settings_tenant_shared_private
  CHECK (tenant_id IS NULL OR public_access = FALSE);

CREATE INDEX IF NOT EXISTS idx_kb_kb_settings_tenant_id
  ON kb_kb_settings (tenant_id);
