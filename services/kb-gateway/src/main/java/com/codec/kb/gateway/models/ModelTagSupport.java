package com.codec.kb.gateway.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ModelTagSupport {
  private static final Pattern FREE_SUFFIX = Pattern.compile("(?i)(:free|(^|[^a-z0-9])free([^a-z0-9]|$))");
  private static final Pattern EMBEDDING_PATTERN = Pattern.compile(
      "(?i)(text-embedding|embedding|embeddings|multilingual-e5|(?:^|[^a-z0-9])e5([^a-z0-9]|$)|bge-(?:m3|large|base|small|en|zh)|gte-|voyage|jina-embeddings|jina-clip|nomic-embed|snowflake-arctic-embed|mxbai-embed|uae-|qwen(?:2|2\\.5|3)?-embedding|gemini-embedding|llm2vec)"
  );
  private static final Pattern RERANK_PATTERN = Pattern.compile(
      "(?i)(rerank|re-rank|reranker|re-ranker|cohere-rerank|bge-reranker|jina-reranker|gte-rerank)"
  );
  private static final Pattern REASONING_PATTERN = Pattern.compile(
      "(?i)(reasoning|reasoner|thinking|think(?:ing)?|deepseek-r1|deepseek-r1-distill|grok-3-reasoning|sonar-reasoning|deep-research|research|gemini-2\\.5|claude-3\\.7-sonnet|claude-sonnet-4|claude-opus-4|qwq|(^|[^a-z0-9])o1([^a-z0-9]|$)|(^|[^a-z0-9])o3([^a-z0-9]|$)|(^|[^a-z0-9])o4([^a-z0-9]|$)|(^|[^a-z0-9])r1([^a-z0-9]|$))"
  );
  private static final Pattern VISION_PATTERN = Pattern.compile(
      "(?i)(vision|visual|multimodal|omni|image|video|pixtral|llava|internvl|minicpm-v|glm-4v|qvq|qwen-vl|qwen2-vl|qwen2\\.5-vl|gpt-4o|gpt-4\\.1|gpt-4-turbo|claude-3|claude-3\\.5|claude-3\\.7|claude-sonnet-4|claude-opus-4|gemini|gemma-3|llama-3\\.2-.*vision|llama-4|doubao-vision|step-1v|moondream)"
  );
  private static final Pattern WEB_SEARCH_PATTERN = Pattern.compile(
      "(?i)(web-search|search-preview|search-grounding|search_context|search_grounding|grounding|grounded|internet|browse|browser|online|sonar|web\\b|(^|[^a-z0-9])search([^a-z0-9]|$)|deep-research)"
  );
  private static final Pattern TOOL_USE_PATTERN = Pattern.compile(
      "(?i)(computer-use|tool-use|browser-use|function-calling|tool[_ -]?choice|function[_ -]?calling|(^|[^a-z0-9])tool(s)?([^a-z0-9]|$)|(^|[^a-z0-9])function(s)?([^a-z0-9]|$)|operator|agentic)"
  );

  private ModelTagSupport() {}

  public static List<String> inferTags(String modelId, String displayName, String providerName) {
    String haystack = lower(String.join(" ", safe(modelId), safe(displayName), safe(providerName)));
    LinkedHashSet<String> tags = new LinkedHashSet<>();

    boolean rerank = isRerankModel(haystack);
    boolean embed = !rerank && isEmbeddingModel(haystack);

    if (isReasoningModel(haystack) && !embed && !rerank) tags.add("reasoning");
    if (isVisionModel(haystack) && !embed && !rerank) tags.add("vision");
    if (isWebSearchModel(haystack) && !embed && !rerank) tags.add("online");
    if (isFreeModel(haystack)) tags.add("free");
    if (embed) tags.add("embed");
    if (rerank) tags.add("rerank");
    if (isToolUseModel(haystack) && !embed && !rerank) tags.add("tool");

    return new ArrayList<>(tags);
  }

  public static boolean isEmbeddingModel(String value) {
    String src = lower(value);
    return !src.isBlank() && EMBEDDING_PATTERN.matcher(src).find();
  }

  public static boolean isRerankModel(String value) {
    String src = lower(value);
    return !src.isBlank() && RERANK_PATTERN.matcher(src).find();
  }

  public static boolean isReasoningModel(String value) {
    String src = lower(value);
    return !src.isBlank() && REASONING_PATTERN.matcher(src).find();
  }

  public static boolean isVisionModel(String value) {
    String src = lower(value);
    return !src.isBlank() && VISION_PATTERN.matcher(src).find();
  }

  public static boolean isWebSearchModel(String value) {
    String src = lower(value);
    return !src.isBlank() && WEB_SEARCH_PATTERN.matcher(src).find();
  }

  public static boolean isToolUseModel(String value) {
    String src = lower(value);
    return !src.isBlank() && TOOL_USE_PATTERN.matcher(src).find();
  }

  public static boolean isFreeModel(String value) {
    String src = lower(value);
    return !src.isBlank() && FREE_SUFFIX.matcher(src).find();
  }

  public static Map<String, Object> capabilities(List<String> tags) {
    LinkedHashSet<String> set = new LinkedHashSet<>();
    if (tags != null) {
      for (String tag : tags) {
        String normalized = lower(tag);
        if (!normalized.isBlank()) set.add(normalized);
      }
    }

    boolean reasoning = set.contains("reasoning");
    boolean vision = set.contains("vision");
    boolean online = set.contains("online");
    boolean free = set.contains("free");
    boolean embed = set.contains("embed");
    boolean rerank = set.contains("rerank");
    boolean tool = set.contains("tool");

    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("reasoning", reasoning);
    out.put("vision", vision);
    out.put("online", online);
    out.put("search", online);
    out.put("web", online);
    out.put("free", free);
    out.put("embedding", embed);
    out.put("embed", embed);
    out.put("rerank", rerank);
    out.put("tools", tool);
    out.put("tool", tool);
    out.put("functionCalling", tool);
    return out;
  }

  public static String lower(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  public static boolean containsAny(String value, String... needles) {
    String src = lower(value);
    if (src.isBlank()) return false;
    for (String needle : needles) {
      String n = lower(needle);
      if (!n.isBlank() && src.contains(n)) return true;
    }
    return false;
  }

  public static boolean containsToken(String value, String... needles) {
    String src = lower(value);
    if (src.isBlank()) return false;
    for (String needle : needles) {
      String n = lower(needle);
      if (n.isBlank()) continue;
      if (Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(n) + "([^a-z0-9]|$)").matcher(src).find()) {
        return true;
      }
    }
    return false;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
