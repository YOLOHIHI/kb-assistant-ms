package com.codec.kb.ai;

import com.codec.kb.common.util.IdUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SessionService {
  private final SessionRepository sessionRepo;
  private final MessageRepository messageRepo;

  public SessionService(SessionRepository sessionRepo, MessageRepository messageRepo) {
    this.sessionRepo = sessionRepo;
    this.messageRepo = messageRepo;
  }

  @Transactional
  public ChatSession create(String userId, String title) {
    SessionEntity e = new SessionEntity();
    e.setId(IdUtil.newId("ses"));
    e.setUserId(safeTenant(userId));
    e.setTitle(title == null || title.isBlank() ? "New Chat" : title.trim());
    Instant now = Instant.now();
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    sessionRepo.save(e);
    return toDto(e);
  }

  @Transactional(readOnly = true)
  public List<ChatSession> list(String userId) {
    return sessionRepo.findByUserIdOrderByUpdatedAtDesc(safeTenant(userId))
        .stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public ChatSession get(String userId, String id) {
    return sessionRepo.findById(id)
        .filter(e -> safeTenant(userId).equals(e.getUserId()))
        .map(this::toDto)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public List<ChatMessage> messages(String userId, String sessionId) {
    return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId)
        .stream()
        .map(m -> new ChatMessage(m.getSender(), m.getContent(),
            m.getCreatedAt().toString(), m.getModel()))
        .toList();
  }

  @Transactional
  public void appendMessage(String sessionId, String sender, String content, String model) {
    MessageEntity m = new MessageEntity();
    m.setSessionId(sessionId);
    m.setSender(sender);
    m.setContent(content);
    m.setModel(model);
    m.setCreatedAt(Instant.now());
    messageRepo.save(m);
  }

  @Transactional
  public ChatSession rename(String userId, String id, String title) {
    String t = title == null ? "" : title.trim();
    if (t.isBlank() || t.length() > 80) return null;
    return sessionRepo.findById(id)
        .filter(e -> safeTenant(userId).equals(e.getUserId()))
        .map(e -> {
          e.setTitle(t);
          e.setUpdatedAt(Instant.now());
          sessionRepo.save(e);
          return toDto(e);
        }).orElse(null);
  }

  @Transactional
  public boolean delete(String userId, String id) {
    return sessionRepo.findById(id)
        .filter(e -> safeTenant(userId).equals(e.getUserId()))
        .map(e -> {
          messageRepo.deleteBySessionId(id);
          sessionRepo.delete(e);
          return true;
        }).orElse(false);
  }

  @Transactional
  public void touch(String userId, String id) {
    sessionRepo.findById(id)
        .filter(e -> safeTenant(userId).equals(e.getUserId()))
        .ifPresent(e -> {
          e.setUpdatedAt(Instant.now());
          sessionRepo.save(e);
        });
  }

  @Transactional(readOnly = true)
  public long countAllSessions() {
    return sessionRepo.countAllSessions();
  }

  private ChatSession toDto(SessionEntity e) {
    return new ChatSession(
        e.getId(), e.getTitle(),
        e.getCreatedAt().toString(),
        e.getUpdatedAt().toString()
    );
  }

  private static String safeTenant(String userId) {
    String s = userId == null ? "" : userId.trim();
    if (s.isBlank()) return "public";
    if (!s.matches("[0-9a-fA-F-]{16,64}")) return "public";
    return s;
  }
}
