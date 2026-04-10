package com.codec.kb.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
  List<SessionEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

  @Query("SELECT COUNT(s) FROM SessionEntity s")
  long countAllSessions();
}
