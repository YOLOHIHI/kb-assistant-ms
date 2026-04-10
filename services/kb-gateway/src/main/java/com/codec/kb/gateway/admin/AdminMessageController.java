package com.codec.kb.gateway.admin;

import com.codec.kb.gateway.auth.AuthUtil;
import com.codec.kb.gateway.store.UserMessageEntity;
import com.codec.kb.gateway.store.UserMessageRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/messages")
public class AdminMessageController {
  private final UserMessageRepository repo;

  public AdminMessageController(UserMessageRepository repo) {
    this.repo = repo;
  }

  public record ReplyRequest(String reply) {}
  public record StatusRequest(String status) {}

  @GetMapping
  public Map<String, Object> listMessages(
      @RequestParam(name = "status", required = false) String status) {
    List<UserMessageEntity> msgs;
    if (status != null && !status.isBlank()) {
      msgs = repo.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
    } else {
      msgs = repo.findAllByOrderByCreatedAtDesc();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (UserMessageEntity m : msgs) {
      out.add(toMap(m));
    }
    return Map.of("messages", out);
  }

  @PostMapping("/{id}/reply")
  public Map<String, Object> replyMessage(@PathVariable("id") String id, @RequestBody ReplyRequest req) {
    UUID adminId = AuthUtil.requirePrincipal().id();
    UUID msgId = parseUuid(id);
    String reply = (req == null || req.reply() == null) ? "" : req.reply().trim();
    if (reply.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回复不能为空");

    UserMessageEntity msg = repo.findById(msgId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
    msg.setReply(reply);
    msg.setRepliedBy(adminId);
    msg.setRepliedAt(Instant.now());
    msg.setStatus("REPLIED");
    repo.save(msg);
    return Map.of("ok", true);
  }

  @PatchMapping("/{id}")
  public Map<String, Object> updateStatus(@PathVariable("id") String id, @RequestBody StatusRequest req) {
    UUID msgId = parseUuid(id);
    String status = (req == null || req.status() == null) ? "" : req.status().trim().toUpperCase();
    if (!List.of("OPEN", "REPLIED", "CLOSED").contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
    }
    UserMessageEntity msg = repo.findById(msgId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
    msg.setStatus(status);
    repo.save(msg);
    return Map.of("ok", true);
  }

  private static Map<String, Object> toMap(UserMessageEntity m) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    map.put("id", m.getId().toString());
    map.put("userId", m.getUserId().toString());
    map.put("subject", m.getSubject());
    map.put("content", m.getContent());
    map.put("status", m.getStatus());
    map.put("reply", m.getReply() == null ? "" : m.getReply());
    map.put("repliedBy", m.getRepliedBy() == null ? "" : m.getRepliedBy().toString());
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
