package com.codec.kb.index;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "documents", schema = "idx")
public class DocumentEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "kb_id", nullable = false, length = 64)
  private String kbId;

  @Column(nullable = false, length = 512)
  private String filename;

  @Column(name = "content_type", length = 128)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(length = 64)
  private String sha256;

  @Column(length = 255)
  private String category;

  /** JSON array of tag strings, stored as text */
  @Column(columnDefinition = "TEXT")
  private String tags;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public DocumentEntity() {}

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getKbId() { return kbId; }
  public void setKbId(String kbId) { this.kbId = kbId; }
  public String getFilename() { return filename; }
  public void setFilename(String filename) { this.filename = filename; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
  public String getSha256() { return sha256; }
  public void setSha256(String sha256) { this.sha256 = sha256; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getTags() { return tags; }
  public void setTags(String tags) { this.tags = tags; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
