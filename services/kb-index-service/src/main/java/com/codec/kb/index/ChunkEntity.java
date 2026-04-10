package com.codec.kb.index;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chunks", schema = "idx")
public class ChunkEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "doc_id", nullable = false, length = 64)
  private String docId;

  @Column(name = "kb_id", nullable = false, length = 64)
  private String kbId;

  @Column(name = "chunk_index", nullable = false)
  private int chunkIndex;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "source_hint", length = 1024)
  private String sourceHint;

  /**
   * Stored as pgvector's vector(512) type.
   * FloatArrayConverter serializes float[] ↔ "[0.1,0.2,...]".
   */
  @Convert(converter = FloatArrayConverter.class)
  @Column(columnDefinition = "TEXT")
  private float[] embedding;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public ChunkEntity() {}

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getDocId() { return docId; }
  public void setDocId(String docId) { this.docId = docId; }
  public String getKbId() { return kbId; }
  public void setKbId(String kbId) { this.kbId = kbId; }
  public int getChunkIndex() { return chunkIndex; }
  public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getSourceHint() { return sourceHint; }
  public void setSourceHint(String sourceHint) { this.sourceHint = sourceHint; }
  public float[] getEmbedding() { return embedding; }
  public void setEmbedding(float[] embedding) { this.embedding = embedding; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
