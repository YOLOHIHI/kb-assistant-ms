package com.codec.kb.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class HttpLocalEmbeddingBackend implements LocalEmbeddingBackend {
  private final IndexServiceConfig cfg;
  private final ObjectMapper om;
  private final HttpClient http;

  public HttpLocalEmbeddingBackend(IndexServiceConfig cfg, ObjectMapper om) {
    this.cfg = cfg;
    this.om = om;
    this.http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build();
  }

  @PreDestroy
  public void close() {
    http.close();
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    if (texts == null || texts.isEmpty()) return List.of();

    var body = java.util.Map.of("texts", texts);
    final String reqJson;
    try {
      reqJson = om.writeValueAsString(body);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize embed request", e);
    }

    final String json;
    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(cfg.embedderUrl() + "/embed"))
          .timeout(Duration.ofSeconds(120))
          .version(HttpClient.Version.HTTP_1_1)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() / 100 != 2) {
        throw new IllegalStateException("embedder HTTP " + resp.statusCode() + ": " + resp.body());
      }
      json = resp.body();
    } catch (Exception e) {
      throw new RuntimeException("Embedding request failed", e);
    }

    try {
      JsonNode root = om.readTree(json);
      JsonNode vecs = root.get("vectors");
      if (vecs == null || !vecs.isArray()) throw new IllegalStateException("bad embed response");
      ArrayList<float[]> out = new ArrayList<>();
      for (JsonNode v : vecs) {
        float[] a = new float[v.size()];
        for (int i = 0; i < v.size(); i++) {
          a[i] = (float) v.get(i).asDouble();
        }
        out.add(a);
      }
      return out;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse embed response", e);
    }
  }
}
