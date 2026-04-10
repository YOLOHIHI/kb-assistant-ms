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
   * pgvector approximate nearest-neighbor search.
   * embedding column is TEXT; cast to vector at query time (requires pgvector extension).
   * If pgvector is not installed this query will throw; IndexService catches and falls back to BM25.
   */
  @Query(value = """
      SELECT id, doc_id, kb_id, chunk_index, content, source_hint,
             1 - (CAST(embedding AS vector) <=> CAST(:queryVec AS vector)) AS score
      FROM idx.chunks
      WHERE kb_id = :kbId AND embedding IS NOT NULL
      ORDER BY CAST(embedding AS vector) <=> CAST(:queryVec AS vector)
      LIMIT :topK
      """, nativeQuery = true)
  List<Object[]> findTopKByDenseSimilarity(
      @Param("kbId") String kbId,
      @Param("queryVec") String queryVec,
      @Param("topK") int topK
  );

  @Query(value = "SELECT COUNT(*) FROM idx.chunks WHERE kb_id = :kbId", nativeQuery = true)
  long countByKbId(@Param("kbId") String kbId);
}
