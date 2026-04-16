package com.codec.kb.gateway.models;

import com.codec.kb.common.ManagedEmbeddingResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProviderEmbeddingService {
  private static final Logger log = LoggerFactory.getLogger(ProviderEmbeddingService.class);
  private final AiModelResolverService resolver;
  private final ObjectMapper om;
  private final HttpClient http;

  public ProviderEmbeddingService(AiModelResolverService resolver, ObjectMapper om) {
    this.resolver = resolver;
    this.om = om;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public ManagedEmbeddingResponse embedByModelRef(String modelRef, List<String> texts) {
    ArrayList<String> input = new ArrayList<>();
    long startedAt = System.nanoTime();
    if (texts != null) {
      for (String text : texts) {
        input.add(text == null ? "" : text);
      }
    }
    if (input.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "texts is required");
    }

    AiModelResolverService.ModelEndpointConfig cfg = resolver.resolveModelConfig(modelRef);
    if (cfg == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embedding model unavailable");
    }
    log.info(
        "Provider embedding request started modelRef={} provider={} model={} texts={}",
        modelRef,
        cfg.providerName(),
        cfg.modelId(),
        input.size()
    );

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", cfg.modelId());
    body.put("input", input);

    final String json;
    ArrayList<String> errors = new ArrayList<>();
    try {
      String reqJson = om.writeValueAsString(body);
      HttpResponse<String> success = null;
      for (String urlBase : candidateBaseUrls(cfg.baseUrl())) {
        String url = trimSlash(urlBase) + "/embeddings";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", "Bearer " + cfg.apiKey())
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
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, String.join(" | ", errors));
      }
      json = success.body();
    } catch (ResponseStatusException e) {
      log.warn(
          "Provider embedding request failed modelRef={} provider={} model={} texts={} elapsedMs={} reason={}",
          modelRef,
          cfg.providerName(),
          cfg.modelId(),
          input.size(),
          elapsedMillis(startedAt),
          e.getReason()
      );
      throw e;
    } catch (Exception e) {
      log.warn(
          "Provider embedding request failed modelRef={} provider={} model={} texts={} elapsedMs={} reason={}",
          modelRef,
          cfg.providerName(),
          cfg.modelId(),
          input.size(),
          elapsedMillis(startedAt),
          e.getMessage()
      );
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding API request failed: " + e.getMessage());
    }

    try {
      JsonNode root = om.readTree(json);
      JsonNode data = root.get("data");
      if (data == null || !data.isArray()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding API bad response: missing data");
      }

      @SuppressWarnings("unchecked")
      List<Double>[] out = new List[input.size()];
      int seen = 0;
      for (JsonNode item : data) {
        int idx = item.has("index") ? item.get("index").asInt(-1) : -1;
        if (idx < 0) idx = seen;
        JsonNode emb = item.get("embedding");
        if (emb == null || !emb.isArray()) {
          throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding API bad response: missing embedding");
        }

        ArrayList<Double> vec = new ArrayList<>(emb.size());
        for (JsonNode value : emb) {
          vec.add(value.asDouble());
        }
        if (idx >= 0 && idx < out.length) out[idx] = vec;
        seen++;
      }

      ArrayList<List<Double>> vectors = new ArrayList<>(out.length);
      for (int i = 0; i < out.length; i++) {
        if (out[i] == null) {
          throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding API bad response: missing vector for index " + i);
        }
        vectors.add(out[i]);
      }
      log.info(
          "Provider embedding request completed modelRef={} provider={} model={} texts={} vectors={} elapsedMs={}",
          modelRef,
          cfg.providerName(),
          cfg.modelId(),
          input.size(),
          vectors.size(),
          elapsedMillis(startedAt)
      );
      return new ManagedEmbeddingResponse(cfg.providerName(), cfg.modelId(), vectors);
    } catch (ResponseStatusException e) {
      log.warn(
          "Provider embedding response parse failed modelRef={} provider={} model={} texts={} elapsedMs={} reason={}",
          modelRef,
          cfg.providerName(),
          cfg.modelId(),
          input.size(),
          elapsedMillis(startedAt),
          e.getReason()
      );
      throw e;
    } catch (Exception e) {
      log.warn(
          "Provider embedding response parse failed modelRef={} provider={} model={} texts={} elapsedMs={} reason={}",
          modelRef,
          cfg.providerName(),
          cfg.modelId(),
          input.size(),
          elapsedMillis(startedAt),
          e.getMessage()
      );
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse Embedding API response: " + e.getMessage());
    }
  }

  private static String trimSlash(String value) {
    if (value == null) return "";
    String out = value.trim();
    while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
    return out;
  }

  private static List<String> candidateBaseUrls(String baseUrl) {
    String base = trimSlash(baseUrl);
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (!base.isBlank()) out.add(base);
    if (!base.isBlank() && !base.endsWith("/v1") && !base.contains("/v1/")) out.add(base + "/v1");
    return new ArrayList<>(out);
  }

  private static String truncate(String value, int max) {
    if (value == null) return "";
    if (value.length() <= max) return value;
    return value.substring(0, max) + "...";
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
