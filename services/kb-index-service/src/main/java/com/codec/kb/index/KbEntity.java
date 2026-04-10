package com.codec.kb.index;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "knowledge_bases", schema = "idx")
public class KbEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "embedding_mode", nullable = false, length = 16)
  private String embeddingMode;

  @Column(name = "embedding_model", length = 255)
  private String embeddingModel;

  @Column(name = "embedding_base_url", length = 255)
  private String embeddingBaseUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public KbEntity() {}

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getEmbeddingMode() { return embeddingMode; }
  public void setEmbeddingMode(String embeddingMode) { this.embeddingMode = embeddingMode; }
  public String getEmbeddingModel() { return embeddingModel; }
  public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
  public String getEmbeddingBaseUrl() { return embeddingBaseUrl; }
  public void setEmbeddingBaseUrl(String embeddingBaseUrl) { this.embeddingBaseUrl = embeddingBaseUrl; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
