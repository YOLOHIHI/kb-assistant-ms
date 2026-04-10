package com.codec.kb.gateway.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KbSettingsRepository extends JpaRepository<KbSettingsEntity, String> {
  List<KbSettingsEntity> findByTenantId(UUID tenantId);

  List<KbSettingsEntity> findByTenantIdAndPublicAccessFalse(UUID tenantId);
}
