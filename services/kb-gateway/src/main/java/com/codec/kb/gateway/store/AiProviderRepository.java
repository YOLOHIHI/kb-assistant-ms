package com.codec.kb.gateway.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiProviderRepository extends JpaRepository<AiProviderEntity, UUID> {
  List<AiProviderEntity> findAllByOrderByNameAsc();

  List<AiProviderEntity> findByEnabledTrueOrderByNameAsc();
}
