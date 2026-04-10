package com.codec.kb.gateway.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiModelRepository extends JpaRepository<AiModelEntity, UUID> {
  Optional<AiModelEntity> findByProvider_IdAndModelId(UUID providerId, String modelId);

  List<AiModelEntity> findByEnabledTrueOrderByDisplayNameAsc();

  List<AiModelEntity> findByEnabledTrueAndProvider_EnabledTrueOrderByDisplayNameAsc();

  List<AiModelEntity> findByProvider_IdOrderByDisplayNameAsc(UUID providerId);
}
