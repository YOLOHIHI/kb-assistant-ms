package com.codec.kb.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class BaseApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
    LinkedHashMap<String, Object> body = new LinkedHashMap<>();
    body.put("status", ex.getStatusCode().value());
    body.put("error", resolveMessage(ex));
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    LinkedHashMap<String, Object> body = new LinkedHashMap<>();
    body.put("status", 400);
    body.put("error", ex.getMessage() == null ? "Bad Request" : ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
    LinkedHashMap<String, Object> body = new LinkedHashMap<>();
    body.put("status", 502);
    body.put("error", ex.getMessage() == null ? "Service error" : ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  protected static String resolveMessage(ResponseStatusException ex) {
    String reason = ex.getReason();
    if (reason != null && !reason.isBlank()) return reason;
    return ex.getStatusCode().toString();
  }
}
