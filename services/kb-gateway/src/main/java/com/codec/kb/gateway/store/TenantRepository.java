package com.codec.kb.gateway.store;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
  Optional<TenantEntity> findByInviteCode(String inviteCode);
  Optional<TenantEntity> findBySlug(String slug);
  boolean existsByInviteCode(String inviteCode);
}
