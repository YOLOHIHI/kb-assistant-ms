package com.codec.kb.gateway.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserKbRepository extends JpaRepository<UserKbEntity, UserKbId> {
  List<UserKbEntity> findByIdUserId(UUID userId);

  List<UserKbEntity> findByIdKbId(String kbId);

  Optional<UserKbEntity> findByIdUserIdAndIsDefaultTrue(UUID userId);

  boolean existsByIdUserIdAndIdKbId(UUID userId, String kbId);

  void deleteByIdKbId(String kbId);
}
