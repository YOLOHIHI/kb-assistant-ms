package com.codec.kb.gateway.store;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kb_user_kb")
public class UserKbEntity {
  @EmbeddedId
  private UserKbId id;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UserKbId getId() {
    return id;
  }

  public void setId(UserKbId id) {
    this.id = id;
  }

  public UUID getUserId() {
    return id == null ? null : id.getUserId();
  }

  public String getKbId() {
    return id == null ? null : id.getKbId();
  }

  public boolean isDefault() {
    return isDefault;
  }

  public void setDefault(boolean aDefault) {
    isDefault = aDefault;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}

