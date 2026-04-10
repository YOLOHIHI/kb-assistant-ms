package com.codec.kb.gateway.store;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserKbId implements Serializable {
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "kb_id", nullable = false, length = 64)
  private String kbId;

  public UserKbId() {}

  public UserKbId(UUID userId, String kbId) {
    this.userId = userId;
    this.kbId = kbId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getKbId() {
    return kbId;
  }

  public void setKbId(String kbId) {
    this.kbId = kbId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserKbId that)) return false;
    return Objects.equals(userId, that.userId) && Objects.equals(kbId, that.kbId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, kbId);
  }
}

