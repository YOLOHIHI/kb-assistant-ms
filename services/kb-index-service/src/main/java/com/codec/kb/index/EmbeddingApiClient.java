package com.codec.kb.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public final class EmbeddingApiClient {
  private final ObjectMapper om;
  private final HttpClient http;

  public EmbeddingApiClient(ObjectMapper om) {
    this.om = om;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  @PreDestroy
  public void close() {
    http.close();
  }

  public List<float[]> embed(String baseUrl, String apiKey, String model, List<String> texts) {
    if (texts == null || texts.isEmpty()) return List.of();

    Map<String, Object> body = Map.of(
        "model", model,
        "input", texts
    );

    final String json;
    try {
      String reqJson = om.writeValueAsString(body);
      HttpResponse<String> success = null;
      ArrayList<String> errors = new ArrayList<>();
      for (String urlBase : candidateBaseUrls(baseUrl)) {
        String url = trimSlash(urlBase) + "/embeddings";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 == 2) {
          success = resp;
          break;
        }
        errors.add(url + " -> HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 240));
      }
      if (success == null) {
        throw new RuntimeException(String.join(" | ", errors));
      }
      json = success.body();
    } catch (Exception e) {
      throw new RuntimeException("Embedding API request failed: " + e.getMessage(), e);
    }

    try {
      JsonNode root = om.readTree(json);
      JsonNode data = root.get("data");
      if (data == null || !data.isArray()) {
        throw new IllegalStateException("Embedding API bad response: missing data");
      }

      float[][] out = new float[texts.size()][];
      int seen = 0;
      for (JsonNode item : data) {
        int idx = item.has("index") ? item.get("index").asInt(-1) : -1;
        if (idx < 0) idx = seen;
        JsonNode emb = item.get("embedding");
        if (emb == null || !emb.isArray()) throw new IllegalStateException("Embedding API bad response: missing embedding");

        float[] vec = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
          vec[i] = (float) emb.get(i).asDouble();
        }

        if (idx >= 0 && idx < out.length) out[idx] = vec;
        seen++;
      }

      ArrayList<float[]> list = new ArrayList<>(out.length);
      for (int i = 0; i < out.length; i++) {
        if (out[i] == null) throw new IllegalStateException("Embedding API bad response: missing vector for index " + i);
        list.add(out[i]);
      }
      return list;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse Embedding API response: " + e.getMessage(), e);
    }
  }

  private static String trimSlash(String s) {
    if (s == null) return "";
    if (s.endsWith("/")) return s.substring(0, s.length() - 1);
    return s;
  }

  private static List<String> candidateBaseUrls(String baseUrl) {
    String base = trimSlash(baseUrl == null ? "" : baseUrl.trim());
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (!base.isBlank()) out.add(base);
    if (!base.isBlank() && !base.endsWith("/v1") && !base.contains("/v1/")) out.add(base + "/v1");
    return new ArrayList<>(out);
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    if (s.length() <= max) return s;
    return s.substring(0, max) + "...";
  }
}
