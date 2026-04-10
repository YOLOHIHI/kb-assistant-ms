package com.codec.kb.doc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Component
public final class OcrClient {
  private final DocServiceConfig cfg;
  private final RestClient rc;
  private final ObjectMapper om;

  public OcrClient(DocServiceConfig cfg, RestClient rc, ObjectMapper om) {
    this.cfg = cfg;
    this.rc = rc;
    this.om = om;
  }

  public boolean enabled() {
    return cfg.ocrUrl() != null && !cfg.ocrUrl().isBlank();
  }

  public String ocr(byte[] bytes, String filename, String contentType) {
    if (!enabled()) return null;

    String b64 = Base64.getEncoder().encodeToString(bytes == null ? new byte[0] : bytes);

    var body = java.util.Map.of(
        "filename", filename == null ? "" : filename,
        "contentType", contentType == null ? "" : contentType,
        "dataBase64", b64
    );

    String json = rc.post()
        .uri(trimSlash(cfg.ocrUrl()) + "/ocr")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);

    try {
      JsonNode root = om.readTree(json);
      JsonNode t = root.get("text");
      if (t == null) return "";
      return t.asText();
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse OCR response", e);
    }
  }

  private static String trimSlash(String s) {
    if (s == null) return "";
    if (s.endsWith("/")) return s.substring(0, s.length() - 1);
    return s;
  }
}
