package com.codec.kb.index;

import com.codec.kb.common.web.BaseApiExceptionHandler;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler extends BaseApiExceptionHandler {

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
    LinkedHashMap<String, Object> body = new LinkedHashMap<>();
    body.put("status", 502);
    body.put("error", ex.getMessage() == null ? "Embedding service error" : ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
