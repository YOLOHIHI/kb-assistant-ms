package com.codec.kb.gateway.store;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserMessageRepository extends JpaRepository<UserMessageEntity, UUID> {
  List<UserMessageEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
  List<UserMessageEntity> findByStatusOrderByCreatedAtDesc(String status);
  List<UserMessageEntity> findAllByOrderByCreatedAtDesc();
}
