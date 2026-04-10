package com.codec.kb.gateway.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kb_kb_settings")
public class KbSettingsEntity {
  @Id
  @Column(name = "kb_id", nullable = false, length = 64)
  private String kbId;

  @Column(name = "document_count", nullable = false)
  private int documentCount;

  @Column(name = "public_access", nullable = false)
  private boolean publicAccess;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public String getKbId() {
    return kbId;
  }

  public void setKbId(String kbId) {
    this.kbId = kbId;
  }

  public int getDocumentCount() {
    return documentCount;
  }

  public void setDocumentCount(int documentCount) {
    this.documentCount = documentCount;
  }

  public boolean isPublicAccess() {
    return publicAccess;
  }

  public void setPublicAccess(boolean publicAccess) {
    this.publicAccess = publicAccess;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
