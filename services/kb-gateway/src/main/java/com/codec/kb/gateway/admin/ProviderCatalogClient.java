package com.codec.kb.gateway.admin;

import com.codec.kb.gateway.crypto.CryptoService;
import com.codec.kb.gateway.models.ModelTagSupport;
import com.codec.kb.gateway.store.AiModelEntity;
import com.codec.kb.gateway.store.AiModelRepository;
import com.codec.kb.gateway.store.AiProviderEntity;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates remote provider model-list fetching and local model-row building.
 * Delegates JSON parsing/normalisation to {@link ModelPayloadParser}.
 */
@Component
public class ProviderCatalogClient {

  private final RestClient rc;
  private final ModelPayloadParser parser;
  private final AiModelRepository models;
  private final CryptoService crypto;

  public ProviderCatalogClient(RestClient rc, ModelPayloadParser parser,
      AiModelRepository models, CryptoService crypto) {
    this.rc = rc;
    this.parser = parser;
    this.models = models;
    this.crypto = crypto;
  }

  // ─── Public API ─────────────────────────────────────────────────────────────

  /**
   * Fetches the raw model-item nodes from the remote provider, trying multiple
   * URL strategies until one succeeds. Throws {@link ResponseStatusException}
   * (502) if every strategy fails.
   */
  public List<JsonNode> fetchProviderModelItems(AiProviderEntity p) {
    String baseUrl = normalizeForFetch(p == null ? null : p.getBaseUrl());
    String apiKey = crypto.decrypt(p == null ? null : p.getApiKeyEnc());
    if (apiKey.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey not set");

    ArrayList<String> errors = new ArrayList<>();
    for (FetchStrategy strategy : buildFetchStrategies(p, baseUrl, apiKey)) {
      try {
        String json = fetchModelPayload(strategy);
        return parser.parseModelItems(json, strategy.label());
      } catch (ResponseStatusException e) {
        errors.add(strategy.label() + " -> " + ModelPayloadParser.safeTrim(e.getReason()));
      } catch (Exception e) {
        errors.add(strategy.label() + " -> " + e.getMessage());
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "failed to fetch models: " + truncate(String.join(" | ", errors), 500)
    );
  }

  /**
   * Builds display rows by merging remote model items with locally stored metadata
   * (enabled flag, overridden display name).
   */
  public List<Map<String, Object>> buildProviderModelRows(AiProviderEntity p, List<JsonNode> remote) {
    Map<String, AiModelEntity> existing = new LinkedHashMap<>();
    for (AiModelEntity model : models.findByProvider_IdOrderByDisplayNameAsc(p.getId())) {
      if (model == null || model.getModelId() == null || model.getModelId().isBlank()) continue;
      existing.put(model.getModelId(), model);
    }

    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (JsonNode item : remote) {
      String modelId = parser.extractModelId(item);
      if (modelId.isBlank()) continue;

      AiModelEntity local = existing.get(modelId);
      String displayName = (local != null && local.getDisplayName() != null && !local.getDisplayName().isBlank())
          ? local.getDisplayName()
          : parser.extractDisplayName(item, modelId);
      List<String> tags = parser.inferOpenRouterTags(item, modelId);

      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", local == null ? "" : String.valueOf(local.getId()));
      row.put("providerId", p.getId() == null ? "" : String.valueOf(p.getId()));
      row.put("providerName", p.getName() == null ? "" : p.getName());
      row.put("modelId", modelId);
      row.put("displayName", displayName);
      row.put("enabled", local != null && local.isEnabled());
      row.put("tags", tags);
      row.put("capabilities", ModelTagSupport.capabilities(tags));
      out.add(row);
    }
    return out;
  }

  /**
   * Fallback: builds display rows from locally stored models when remote fetch fails.
   */
  public List<Map<String, Object>> buildLocalModelRows(AiProviderEntity p) {
    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (AiModelEntity model : models.findByProvider_IdOrderByDisplayNameAsc(p.getId())) {
      if (model == null) continue;
      List<String> tags = new ArrayList<>(ModelTagSupport.inferTags(
          model.getModelId(), model.getDisplayName(), p.getName()
      ));

      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", model.getId() == null ? "" : String.valueOf(model.getId()));
      row.put("providerId", p.getId() == null ? "" : String.valueOf(p.getId()));
      row.put("providerName", p.getName() == null ? "" : p.getName());
      row.put("modelId", model.getModelId() == null ? "" : model.getModelId());
      row.put("displayName", model.getDisplayName() == null ? "" : model.getDisplayName());
      row.put("enabled", model.isEnabled());
      row.put("tags", tags);
      row.put("capabilities", ModelTagSupport.capabilities(tags));
      out.add(row);
    }
    return out;
  }

  // ─── Fetch strategy helpers ──────────────────────────────────────────────────

  private record FetchStrategy(String label, String url, Map<String, String> headers) {}

  private List<FetchStrategy> buildFetchStrategies(AiProviderEntity p, String baseUrl, String apiKey) {
    ArrayList<FetchStrategy> out = new ArrayList<>();
    String providerName = ModelPayloadParser.safeTrim(p == null ? null : p.getName());

    addFetchStrategy(out, "openai:/models", baseUrl + "/models", Map.of(
        "Authorization", "Bearer " + apiKey,
        "Accept", "application/json"
    ));

    String v1Base = appendPathSegment(baseUrl, "v1");
    if (!v1Base.equals(baseUrl)) {
      addFetchStrategy(out, "openai:/v1/models", v1Base + "/models", Map.of(
          "Authorization", "Bearer " + apiKey,
          "Accept", "application/json"
      ));
    }

    if (looksLikeAnthropic(baseUrl, providerName)) {
      addFetchStrategy(out, "anthropic:/models", baseUrl + "/models", Map.of(
          "x-api-key", apiKey,
          "anthropic-version", "2023-06-01",
          "Accept", "application/json"
      ));
      if (!v1Base.equals(baseUrl)) {
        addFetchStrategy(out, "anthropic:/v1/models", v1Base + "/models", Map.of(
            "x-api-key", apiKey,
            "anthropic-version", "2023-06-01",
            "Accept", "application/json"
        ));
      }
    }

    if (looksLikeGemini(baseUrl, providerName)) {
      String geminiBase = stripSuffix(baseUrl, "/openai");
      String queryKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
      addFetchStrategy(out, "gemini:/models?key", geminiBase + "/models?key=" + queryKey, Map.of(
          "Accept", "application/json"
      ));
      addFetchStrategy(out, "gemini:/models x-goog-api-key", geminiBase + "/models", Map.of(
          "x-goog-api-key", apiKey,
          "Accept", "application/json"
      ));
    }

    if (looksLikeOllama(baseUrl, providerName)) {
      String ollamaBase = stripVersionSuffix(baseUrl);
      addFetchStrategy(out, "ollama:/api/tags", ollamaBase + "/api/tags", Map.of(
          "Accept", "application/json"
      ));
    }

    return out;
  }

  private void addFetchStrategy(List<FetchStrategy> out, String label, String url, Map<String, String> headers) {
    String normalizedUrl = ModelPayloadParser.safeTrim(url);
    if (normalizedUrl.isBlank()) return;
    Map<String, String> normalizedHeaders = headers == null ? Map.of() : headers;
    for (FetchStrategy existing : out) {
      if (existing.url().equals(normalizedUrl) && existing.headers().equals(normalizedHeaders)) return;
    }
    out.add(new FetchStrategy(label, normalizedUrl, normalizedHeaders));
  }

  private String fetchModelPayload(FetchStrategy strategy) {
    try {
      var req = rc.get().uri(URI.create(strategy.url()));
      for (Map.Entry<String, String> header : strategy.headers().entrySet()) {
        if (header.getKey() == null || header.getValue() == null || header.getValue().isBlank()) continue;
        req = req.header(header.getKey(), header.getValue());
      }
      return req.retrieve().body(String.class);
    } catch (RestClientResponseException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "upstream HTTP " + e.getRawStatusCode() + " " + truncate(e.getResponseBodyAsString(), 240)
      );
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "request failed: " + e.getMessage());
    }
  }

  // ─── Provider detection ──────────────────────────────────────────────────────

  private static boolean looksLikeAnthropic(String baseUrl, String providerName) {
    String src = ModelPayloadParser.lower(baseUrl + " " + providerName);
    return src.contains("anthropic");
  }

  private static boolean looksLikeGemini(String baseUrl, String providerName) {
    String src = ModelPayloadParser.lower(baseUrl + " " + providerName);
    return src.contains("gemini") || src.contains("googleapis.com") || src.contains("generativelanguage");
  }

  private static boolean looksLikeOllama(String baseUrl, String providerName) {
    String src = ModelPayloadParser.lower(baseUrl + " " + providerName);
    return src.contains("ollama") || src.contains("11434") || src.contains("localhost");
  }

  // ─── URL normalisation helpers ───────────────────────────────────────────────

  /**
   * Strips well-known path suffixes and trailing slashes for use when reading a
   * provider baseUrl from the database (less strict than the create/update validation).
   */
  private static String normalizeForFetch(String baseUrl) {
    String s = ModelPayloadParser.safeTrim(baseUrl);
    if (s.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl not set");
    if (s.length() > 240) s = s.substring(0, 240);
    while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
    if (s.endsWith("/chat/completions")) s = s.substring(0, s.length() - "/chat/completions".length());
    if (s.endsWith("/responses")) s = s.substring(0, s.length() - "/responses".length());
    if (s.endsWith("/embeddings")) s = s.substring(0, s.length() - "/embeddings".length());
    if (s.endsWith("/models")) s = s.substring(0, s.length() - "/models".length());
    if (s.endsWith("/api/tags")) s = s.substring(0, s.length() - "/api/tags".length());
    while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
    if (s.endsWith("/api")) s = s + "/v1";
    return s;
  }

  private static String appendPathSegment(String baseUrl, String segment) {
    String base = ModelPayloadParser.safeTrim(baseUrl);
    String seg = ModelPayloadParser.safeTrim(segment);
    if (base.isBlank() || seg.isBlank()) return base;
    if (base.endsWith("/" + seg) || base.contains("/" + seg + "/")) return base;
    return trimTrailingSlash(base) + "/" + seg;
  }

  private static String stripVersionSuffix(String baseUrl) {
    String out = trimTrailingSlash(baseUrl);
    for (String suffix : List.of("/v1", "/v1beta", "/v1beta/openai", "/openai")) {
      out = stripSuffix(out, suffix);
    }
    return trimTrailingSlash(out);
  }

  private static String stripSuffix(String value, String suffix) {
    String src = ModelPayloadParser.safeTrim(value);
    String end = ModelPayloadParser.safeTrim(suffix);
    if (src.endsWith(end)) return src.substring(0, src.length() - end.length());
    return src;
  }

  private static String trimTrailingSlash(String value) {
    String out = ModelPayloadParser.safeTrim(value);
    while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
    return out;
  }

  private static String truncate(String value, int max) {
    if (value == null) return "";
    if (value.length() <= max) return value;
    return value.substring(0, max) + "...";
  }
}
