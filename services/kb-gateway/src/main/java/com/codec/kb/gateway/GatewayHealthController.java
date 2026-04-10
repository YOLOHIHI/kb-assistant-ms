package com.codec.kb.gateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class GatewayHealthController {
  private final GatewayConfig cfg;
  private final RestClient healthRc;

  public GatewayHealthController(
      GatewayConfig cfg,
      @Qualifier("healthRestClient") RestClient healthRc) {
    this.cfg = cfg;
    this.healthRc = healthRc;
  }

  @GetMapping("/api/health")
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, String> targets = new LinkedHashMap<>();
    targets.put("ai", cfg.aiUrl() + "/internal/health");
    targets.put("doc", cfg.docUrl() + "/internal/health");
    targets.put("index", cfg.indexUrl() + "/internal/health");

    Map<String, Object> services = new LinkedHashMap<>();
    boolean allOk = true;
    for (Map.Entry<String, String> entry : targets.entrySet()) {
      Map<String, Object> result = probe(entry.getValue());
      services.put(entry.getKey(), result);
      if (!"ok".equals(result.get("status"))) {
        allOk = false;
      }
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", allOk ? "ok" : "degraded");
    body.put("time", Instant.now().toString());
    body.put("services", services);
    return ResponseEntity
        .status(allOk ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(body);
  }

  @GetMapping("/api/health/live")
  public Map<String, Object> live() {
    return Map.of("status", "ok", "time", Instant.now().toString());
  }

  private Map<String, Object> probe(String url) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("url", url);
    try {
      ResponseEntity<Void> response = healthRc.get().uri(url).retrieve().toBodilessEntity();
      result.put("status", "ok");
      result.put("httpStatus", response.getStatusCode().value());
    } catch (Exception e) {
      result.put("status", "unreachable");
      String message = e.getMessage();
      result.put(
          "error",
          message != null ? message.substring(0, Math.min(message.length(), 200)) : "unknown");
    }
    return result;
  }
}
