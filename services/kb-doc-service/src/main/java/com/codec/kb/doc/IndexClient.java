package com.codec.kb.doc;

import com.codec.kb.common.UpsertRequest;
import com.codec.kb.common.UpsertResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class IndexClient {
  private static final ObjectMapper OM = new ObjectMapper();

  private final DocServiceConfig cfg;
  private final RestClient rc;

  public IndexClient(DocServiceConfig cfg, RestClient rc) {
    this.cfg = cfg;
    this.rc = rc;
  }

  public void upsert(String kbId, UpsertRequest req) {
    String id = (kbId == null || kbId.isBlank()) ? "default" : kbId.trim();
    try {
      rc.post()
          .uri(cfg.indexUrl() + "/internal/kbs/" + id + "/upsert")
          .header("X-Internal-Token", cfg.internalToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(req)
          .retrieve()
          .body(UpsertResponse.class);
    } catch (RestClientResponseException e) {
      HttpStatus upstream = HttpStatus.resolve(e.getStatusCode().value());
      String detail = extractMessage(e);
      String reason = detail.isBlank() ? "索引服务错误" : "索引服务错误：" + detail;
      if (upstream != null && upstream.is4xxClientError()) {
        throw new ResponseStatusException(upstream, reason, e);
      }
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason, e);
    } catch (RestClientException e) {
      String detail = e.getMessage() == null ? "" : e.getMessage().trim();
      String reason = detail.isBlank() ? "索引服务不可用" : "索引服务不可用：" + detail;
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason, e);
    }
  }

  public void upsert(UpsertRequest req) {
    upsert("default", req);
  }

  private static String extractMessage(RestClientResponseException e) {
    String body = e.getResponseBodyAsString();
    if (body == null || body.isBlank()) return "";
    try {
      JsonNode root = OM.readTree(body);
      for (String field : new String[]{"error", "message", "reason"}) {
        JsonNode value = root.path(field);
        if (value.isTextual()) {
          String text = value.asText().trim();
          if (!text.isBlank()) return text;
        }
      }
    } catch (Exception ignored) {}
    return body.length() > 240 ? body.substring(0, 240) + "..." : body;
  }
}
