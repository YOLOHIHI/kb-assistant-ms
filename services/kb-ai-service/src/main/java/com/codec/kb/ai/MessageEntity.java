package com.codec.kb.ai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages", schema = "ai")
public class MessageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 64)
  private String sessionId;

  /** 'USER' or 'ASSISTANT' */
  @Column(nullable = false, length = 16)
  private String sender;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(length = 255)
  private String model;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public MessageEntity() {}

  public Long getId() { return id; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public String getSender() { return sender; }
  public void setSender(String sender) { this.sender = sender; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
