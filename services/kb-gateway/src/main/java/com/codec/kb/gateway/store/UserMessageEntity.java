package com.codec.kb.gateway.store;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_message")
public class UserMessageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "subject", nullable = false, length = 200)
  private String subject;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "status", nullable = false, length = 16)
  private String status = "OPEN";

  @Column(name = "reply", columnDefinition = "text")
  private String reply;

  @Column(name = "replied_by")
  private UUID repliedBy;

  @Column(name = "replied_at")
  private Instant repliedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getSubject() { return subject; }
  public void setSubject(String subject) { this.subject = subject; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getReply() { return reply; }
  public void setReply(String reply) { this.reply = reply; }
  public UUID getRepliedBy() { return repliedBy; }
  public void setRepliedBy(UUID repliedBy) { this.repliedBy = repliedBy; }
  public Instant getRepliedAt() { return repliedAt; }
  public void setRepliedAt(Instant repliedAt) { this.repliedAt = repliedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

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
