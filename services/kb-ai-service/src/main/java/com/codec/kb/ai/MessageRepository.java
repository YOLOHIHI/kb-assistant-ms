package com.codec.kb.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
  List<MessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

  @Modifying
  @Query("DELETE FROM MessageEntity m WHERE m.sessionId = :sid")
  void deleteBySessionId(@Param("sid") String sessionId);
}
