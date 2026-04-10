package com.codec.kb.gateway.admin;

import com.codec.kb.common.util.WebUtils;
import com.codec.kb.gateway.models.ModelTagSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Converts raw provider JSON payloads (OpenRouter, OpenAI, Anthropic, Gemini, Ollama)
 * into normalised model field values used by ProviderCatalogClient and AdminProvidersController.
 * All methods are pure/stateless; this class has no mutable state.
 */
@Component
public class ModelPayloadParser {
  private final ObjectMapper om;

  public ModelPayloadParser(ObjectMapper om) {
    this.om = om;
  }

  // ─── Public parsing entry-points ───────────────────────────────────────────

  /**
   * Parses the raw JSON body of a /models response into a list of model-item nodes.
   * Handles OpenAI-style {@code {"data":[...]}} and Ollama-style {@code {"models":[...]}}
   * as well as bare arrays.
   */
  public List<JsonNode> parseModelItems(String json, String strategyLabel) {
    try {
      JsonNode root = om.readTree(json);
      JsonNode data = extractModelArray(root);
      if (data == null || !data.isArray()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            strategyLabel + " bad response: expected array in data/models/result/list/items");
      }
      ArrayList<JsonNode> out = new ArrayList<>();
      for (JsonNode item : data) {
        if (item == null) continue;
        if (item.isObject()) { out.add(item); continue; }
        if (item.isTextual()) out.add(om.createObjectNode().put("id", item.asText("")));
      }
      return out;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          strategyLabel + " parse failed: " + e.getMessage());
    }
  }

  /** Extracts and normalises the model ID from a raw provider model node. */
  public String extractModelId(JsonNode item) {
    String modelId = safeModelId(text(item, "id"));
    if (modelId.isBlank()) modelId = safeModelId(text(item, "model"));
    if (modelId.isBlank()) modelId = safeModelId(text(item, "model_id"));
    if (modelId.isBlank()) modelId = safeModelId(text(item, "slug"));
    if (modelId.isBlank()) modelId = safeModelId(text(item, "name"));
    if (modelId.startsWith("models/")) modelId = modelId.substring("models/".length());
    return modelId;
  }

  /** Extracts a human-readable display name from a raw provider model node. */
  public String extractDisplayName(JsonNode item, String modelId) {
    String displayName = safeTrim(text(item, "display_name"));
    if (displayName.isBlank()) displayName = safeTrim(text(item, "displayName"));
    if (displayName.isBlank()) displayName = safeTrim(text(item, "name"));
    if (displayName.isBlank()) displayName = safeTrim(text(item, "label"));
    if (displayName.isBlank()) displayName = safeTrim(text(item, "model"));
    if (displayName.isBlank()) displayName = safeTrim(text(item, "id"));
    if (displayName.startsWith("models/")) displayName = displayName.substring("models/".length());
    if (displayName.isBlank()) displayName = safeTrim(modelId);
    return displayName;
  }

  /**
   * Infers OpenRouter-style capability tags for a model node. Merges results from
   * {@link ModelTagSupport#inferTags} with additional signals from the node's
   * architecture/supported_parameters fields.
   */
  public List<String> inferOpenRouterTags(JsonNode item, String modelId) {
    LinkedHashSet<String> tags = new LinkedHashSet<>(ModelTagSupport.inferTags(
        modelId,
        String.join(" ", text(item, "name"), text(item, "description"), text(item, "canonical_slug")),
        ""
    ));
    String id = lower(modelId);
    String name = lower(text(item, "name"));
    String desc = lower(text(item, "description"));
    String slug = lower(text(item, "canonical_slug"));
    String modality = lower(item.path("architecture").path("modality").asText(""));
    List<String> inputModalities = lowerList(item.path("architecture").path("input_modalities"));
    List<String> outputModalities = lowerList(item.path("architecture").path("output_modalities"));
    List<String> supportedParameters = lowerList(item.path("supported_parameters"));
    List<String> supportedGenerationMethods = lowerList(item.path("supported_generation_methods"));
    String haystack = String.join(" ", id, name, desc, slug, modality,
        String.join(" ", inputModalities), String.join(" ", outputModalities),
        String.join(" ", supportedParameters), String.join(" ", supportedGenerationMethods));

    if (listContainsAny(supportedParameters, "reasoning", "reasoning_effort", "include_reasoning")
        || listContainsAny(supportedGenerationMethods, "generatecontent") && ModelTagSupport.isReasoningModel(haystack)
        || ModelTagSupport.isReasoningModel(haystack)) tags.add("reasoning");
    if (listContainsAny(inputModalities, "image", "video") || listContainsAny(outputModalities, "image", "video")
        || listContainsAny(supportedGenerationMethods, "generateimages")
        || ModelTagSupport.isVisionModel(haystack)) tags.add("vision");
    if (listContainsAny(supportedParameters, "web_search", "search_context", "search_grounding")
        || ModelTagSupport.isWebSearchModel(haystack)) tags.add("online");
    if (isFreeModel(item) || ModelTagSupport.isFreeModel(haystack)) tags.add("free");
    if ("embedding".equals(modality) || ModelTagSupport.isEmbeddingModel(haystack)) tags.add("embed");
    if ("rerank".equals(modality) || ModelTagSupport.isRerankModel(haystack)) tags.add("rerank");
    if (listContainsAny(supportedParameters, "tools", "tool_choice", "function_calling")
        || ModelTagSupport.isToolUseModel(haystack)) tags.add("tool");
    return new ArrayList<>(tags);
  }

  // ─── Sanitise helpers ──────────────────────────────────────────────────────

  public static String safeModelId(String modelId) {
    String s = modelId == null ? "" : modelId.trim();
    if (s.length() > 120) s = s.substring(0, 120);
    return s;
  }

  public static String safeDisplayName(String displayName) {
    String s = displayName == null ? "" : displayName.trim();
    if (s.length() > 120) s = s.substring(0, 120);
    return s;
  }

  public static boolean shouldUpdateDisplayName(String current, String modelId, String incoming) {
    String cur = current == null ? "" : current.trim();
    String inc = incoming == null ? "" : incoming.trim();
    if (inc.isBlank()) return false;
    if (cur.isBlank()) return true;
    if (modelId != null && !modelId.isBlank() && cur.equals(modelId)) return true;
    return false;
  }

  // ─── Low-level helpers ─────────────────────────────────────────────────────

  private static JsonNode extractModelArray(JsonNode root) {
    return extractModelArray(root, 0);
  }

  private static JsonNode extractModelArray(JsonNode root, int depth) {
    if (root == null || root.isNull() || root.isMissingNode() || depth > 3) return null;
    if (root.isArray()) return root;
    for (String field : List.of("data", "models", "result", "list", "items", "body")) {
      JsonNode node = root.get(field);
      if (node == null || node.isNull() || node.isMissingNode()) continue;
      if (node.isArray()) return node;
      if (node.isObject()) {
        JsonNode nested = extractModelArray(node, depth + 1);
        if (nested != null && nested.isArray()) return nested;
      }
    }
    return null;
  }

  private static boolean isFreeModel(JsonNode item) {
    JsonNode pricing = item.path("pricing");
    if (pricing.isMissingNode() || pricing.isNull()) return false;
    boolean seen = false;
    for (String field : List.of("prompt", "completion", "request", "image", "web_search", "internal_reasoning")) {
      JsonNode value = pricing.path(field);
      if (value.isMissingNode() || value.isNull()) continue;
      String text = value.asText("").trim();
      if (text.isBlank()) continue;
      seen = true;
      try { if (Double.parseDouble(text) > 0D) return false; } catch (Exception ignored) { return false; }
    }
    return seen;
  }

  static String text(JsonNode item, String field) {
    return item != null && item.hasNonNull(field) ? item.get(field).asText("") : "";
  }

  private static List<String> lowerList(JsonNode node) {
    ArrayList<String> out = new ArrayList<>();
    if (node == null || !node.isArray()) return out;
    for (JsonNode item : node) {
      String text = item == null ? "" : item.asText("");
      if (!text.isBlank()) out.add(lower(text));
    }
    return out;
  }

  static String lower(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  static String safeTrim(String value) {
    return WebUtils.safeTrim(value);
  }

  private static boolean listContainsAny(Collection<String> values, String... needles) {
    if (values == null || values.isEmpty()) return false;
    for (String value : values) { if (containsAny(value, needles)) return true; }
    return false;
  }

  private static boolean containsAny(String value, String... needles) {
    String src = lower(value);
    if (src.isBlank()) return false;
    for (String needle : needles) {
      String n = lower(needle);
      if (!n.isBlank() && src.contains(n)) return true;
    }
    return false;
  }
}
