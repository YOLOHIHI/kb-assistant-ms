package com.codec.kb.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, String> {
  List<DocumentEntity> findByKbId(String kbId);

  long countByKbId(String kbId);

  @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM DocumentEntity d WHERE d.kbId = :kbId")
  Long sumSizeBytesByKbId(@Param("kbId") String kbId);

  @Modifying
  @Query("DELETE FROM DocumentEntity d WHERE d.kbId = :kbId")
  void deleteByKbId(@Param("kbId") String kbId);
}
