package com.codec.kb.ai;

import com.codec.kb.common.ChatResponse;
import com.codec.kb.common.ChatRequest;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AiController {
  private final RagService rag;
  private final SessionService sessions;

  public AiController(RagService rag, SessionService sessions) {
    this.rag = rag;
    this.sessions = sessions;
  }

  @GetMapping("/internal/health")
  public Object health() {
    return Map.of("status", "ok", "time", Instant.now().toString());
  }

  @PostMapping(path = "/internal/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ChatResponse chat(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @RequestBody ChatRequest req) {
    return rag.chat(userId, req);
  }

  @PostMapping(path = "/internal/chat/stream",
               consumes = MediaType.APPLICATION_JSON_VALUE,
               produces = "text/event-stream")
  public void chatStream(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @RequestBody ChatRequest req,
      HttpServletResponse resp) throws java.io.IOException {
    resp.setContentType("text/event-stream;charset=UTF-8");
    resp.setHeader("Cache-Control", "no-cache");
    rag.chatStream(userId, req, resp.getOutputStream());
  }

  @GetMapping("/internal/sessions")
  public Map<String, Object> listSessions(
      @RequestHeader(name = "X-User-Id", required = false) String userId) {
    return Map.of("sessions", sessions.list(userId));
  }

  @PostMapping(path = "/internal/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ChatSession createSession(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @RequestBody Map<String, Object> body) {
    String title = body.get("title") == null ? "New Chat" : String.valueOf(body.get("title"));
    return sessions.create(userId, title);
  }

  @PatchMapping(path = "/internal/sessions/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Object renameSession(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> body) {
    String title = body == null || body.get("title") == null ? "" : String.valueOf(body.get("title"));
    ChatSession s = sessions.rename(userId, id, title);
    if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
    return s;
  }

  @GetMapping("/internal/sessions/{id}")
  public Object getSession(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @PathVariable("id") String id) {
    ChatSession s = sessions.get(userId, id);
    if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");

    List<ChatMessage> msgs = sessions.messages(userId, id);
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("id", s.id());
    out.put("title", s.title());
    out.put("createdAt", s.createdAt());
    out.put("updatedAt", s.updatedAt());
    out.put("messages", msgs);
    return out;
  }

  @DeleteMapping("/internal/sessions/{id}")
  public Map<String, Object> deleteSession(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @PathVariable("id") String id) {
    return Map.of("ok", sessions.delete(userId, id));
  }

  @GetMapping("/internal/stats")
  public Map<String, Object> stats() {
    return Map.of(
        "sessions", sessions.countAllSessions(),
        "updatedAt", Instant.now().toString()
    );
  }
}
