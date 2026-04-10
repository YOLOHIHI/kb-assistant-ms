package com.codec.kb.index;

import com.codec.kb.common.ManagedEmbeddingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public final class ManagedEmbeddingClient {
  private final IndexServiceConfig cfg;
  private final ObjectMapper om;
  private final HttpClient http;

  public ManagedEmbeddingClient(IndexServiceConfig cfg, ObjectMapper om) {
    this.cfg = cfg;
    this.om = om;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public List<float[]> embed(String modelRef, List<String> texts) {
    if (texts == null || texts.isEmpty()) return List.of();

    String gatewayUrl = trimSlash(cfg.gatewayUrl());
    if (gatewayUrl.isBlank()) {
      throw new IllegalStateException("Managed embedding requires KB_GATEWAY_URL");
    }

    final String json;
    try {
      String reqJson = om.writeValueAsString(new ManagedEmbeddingRequest(modelRef, texts));
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(gatewayUrl + "/internal/embeddings"))
          .timeout(Duration.ofSeconds(120))
          .header("X-Internal-Token", cfg.internalToken())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Managed embedding HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 500));
      }
      json = resp.body();
    } catch (Exception e) {
      throw new RuntimeException("Managed embedding request failed: " + e.getMessage(), e);
    }

    try {
      JsonNode root = om.readTree(json);
      JsonNode vectors = root.get("vectors");
      if (vectors == null || !vectors.isArray()) {
        throw new IllegalStateException("Managed embedding bad response: missing vectors");
      }

      ArrayList<float[]> out = new ArrayList<>(vectors.size());
      for (JsonNode vector : vectors) {
        if (vector == null || !vector.isArray()) {
          throw new IllegalStateException("Managed embedding bad response: invalid vector");
        }
        float[] row = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
          row[i] = (float) vector.get(i).asDouble();
        }
        out.add(row);
      }
      if (out.size() != texts.size()) {
        throw new IllegalStateException("Managed embedding bad response: vector count mismatch");
      }
      return out;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse managed embedding response: " + e.getMessage(), e);
    }
  }

  private static String trimSlash(String s) {
    if (s == null) return "";
    if (s.endsWith("/")) return s.substring(0, s.length() - 1);
    return s;
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    if (s.length() <= max) return s;
    return s.substring(0, max) + "...";
  }
}
