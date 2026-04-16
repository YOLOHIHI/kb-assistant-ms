package com.codec.kb.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChunkJpaRepository extends JpaRepository<ChunkEntity, String> {
  List<ChunkEntity> findByKbId(String kbId);
  List<ChunkEntity> findByDocId(String docId);

  @Modifying
  @Query("DELETE FROM ChunkEntity c WHERE c.docId = :docId")
  void deleteByDocId(@Param("docId") String docId);

  @Modifying
  @Query("DELETE FROM ChunkEntity c WHERE c.kbId = :kbId")
  void deleteByKbId(@Param("kbId") String kbId);

  /**
   * Native pgvector dense search.
   * The column uses pgvector's vector type directly, while the incoming query string
   * is cast once at the SQL boundary.
   */
  @Query(value = """
      SELECT id, doc_id, kb_id, chunk_index, content, source_hint,
             1 - (embedding <=> CAST(:queryVec AS vector)) AS score
      FROM idx.chunks
      WHERE kb_id = :kbId
        AND embedding IS NOT NULL
        AND vector_dims(embedding) = vector_dims(CAST(:queryVec AS vector))
      ORDER BY embedding <=> CAST(:queryVec AS vector)
      LIMIT :topK
      """, nativeQuery = true)
  List<Object[]> findTopKByDenseSimilarity(
      @Param("kbId") String kbId,
      @Param("queryVec") String queryVec,
      @Param("topK") int topK
  );

  @Query(value = "SELECT COUNT(*) FROM idx.chunks WHERE kb_id = :kbId", nativeQuery = true)
  long countByKbId(@Param("kbId") String kbId);

  @Query(value = "SELECT COUNT(*) FROM idx.chunks WHERE kb_id = :kbId AND embedding IS NOT NULL", nativeQuery = true)
  long countByKbIdAndEmbeddingIsNotNull(@Param("kbId") String kbId);
}
