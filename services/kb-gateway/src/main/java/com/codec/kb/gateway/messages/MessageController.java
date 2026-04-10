package com.codec.kb.gateway.messages;

import com.codec.kb.gateway.auth.AuthUtil;
import com.codec.kb.gateway.store.UserMessageEntity;
import com.codec.kb.gateway.store.UserMessageRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
  private final UserMessageRepository repo;

  public MessageController(UserMessageRepository repo) {
    this.repo = repo;
  }

  public record SendMessageRequest(String subject, String content) {}

  @PostMapping
  public Map<String, Object> sendMessage(@RequestBody SendMessageRequest req) {
    UUID userId = AuthUtil.requirePrincipal().id();
    String subject = (req == null || req.subject() == null) ? "" : req.subject().trim();
    String content = (req == null || req.content() == null) ? "" : req.content().trim();
    if (subject.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主题不能为空");
    if (subject.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主题过长");
    if (content.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不能为空");

    UserMessageEntity msg = new UserMessageEntity();
    msg.setUserId(userId);
    msg.setSubject(subject);
    msg.setContent(content);
    msg.setStatus("OPEN");
    repo.save(msg);
    return Map.of("ok", true, "id", msg.getId().toString());
  }

  @GetMapping
  public Map<String, Object> listMyMessages() {
    UUID userId = AuthUtil.requirePrincipal().id();
    List<UserMessageEntity> msgs = repo.findByUserIdOrderByCreatedAtDesc(userId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (UserMessageEntity m : msgs) {
      out.add(toMap(m));
    }
    return Map.of("messages", out);
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> deleteMessage(@PathVariable("id") String id) {
    UUID userId = AuthUtil.requirePrincipal().id();
    UUID msgId = parseUuid(id);
    UserMessageEntity msg = repo.findById(msgId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
    if (!msg.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }
    repo.delete(msg);
    return Map.of("ok", true);
  }

  private static Map<String, Object> toMap(UserMessageEntity m) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    map.put("id", m.getId().toString());
    map.put("subject", m.getSubject());
    map.put("content", m.getContent());
    map.put("status", m.getStatus());
    map.put("reply", m.getReply() == null ? "" : m.getReply());
    map.put("repliedAt", m.getRepliedAt() == null ? "" : m.getRepliedAt().toString());
    map.put("createdAt", m.getCreatedAt() == null ? "" : m.getCreatedAt().toString());
    return map;
  }

  private static UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
    }
  }
}
