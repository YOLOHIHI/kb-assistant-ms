package com.codec.kb.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public final class OpenAiChatClient {
  private final ObjectMapper om;
  private final HttpClient http;

  public OpenAiChatClient(ObjectMapper om) {
    this.om = om;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /**
   * Simple two-message call: system + user.
   */
  public String chat(String baseUrl, String apiKey, String model,
                     String systemPrompt, String userPrompt) {
    String systemRole = systemRoleForModel(model);
    ArrayList<Map<String, String>> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(Map.of("role", systemRole, "content", systemPrompt));
    }
    messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
    return chatWithMessages(baseUrl, apiKey, model, messages);
  }

  /**
   * Full messages-array call supporting multi-turn conversation history.
   * The messages list should already include a system message as first element if needed.
   */
  public String chatWithMessages(String baseUrl, String apiKey, String model,
                                 List<Map<String, String>> messages) {
    String urlBase = trimSlash(baseUrl);
    String key = apiKey == null ? "" : apiKey.trim();
    String mid = model == null ? "" : model.trim();
    if (urlBase.isBlank() || key.isBlank() || mid.isBlank()) return null;

    ArrayList<String> errors = new ArrayList<>();
    for (String candidateBase : candidateBaseUrls(urlBase)) {
      try {
        String text = callChatCompletions(candidateBase, key, mid, messages);
        if (text != null && !text.isBlank()) return text.trim();
      } catch (Exception e) {
        errors.add(e.getMessage());
      }
    }

    if (errors.isEmpty()) return null;
    throw new RuntimeException("OpenAI-compatible call failed: " + String.join(" | ", errors));
  }

  /**
   * Streaming variant: opens an SSE connection to the LLM with "stream":true, reads
   * delta tokens line-by-line, writes each token as an SSE "token" event to {@code out},
   * and appends all tokens to {@code accumulator}.
   *
   * @param baseUrl     LLM base URL (e.g. https://api.openai.com/v1)
   * @param apiKey      Bearer token
   * @param model       Model ID
   * @param messages    Full messages list (system + history + current user message)
   * @param out         OutputStream to write SSE events to
   * @param accumulator StringBuilder that receives the full assembled answer
   */
  public void streamChat(String baseUrl, String apiKey, String model,
                         List<Map<String, String>> messages,
                         java.io.OutputStream out,
                         StringBuilder accumulator) throws Exception {
    String urlBase = trimSlash(baseUrl);
    String key = apiKey == null ? "" : apiKey.trim();
    String mid = model == null ? "" : model.trim();
    if (urlBase.isBlank() || key.isBlank() || mid.isBlank()) return;

    for (String candidateBase : candidateBaseUrls(urlBase)) {
      try {
        streamChatCompletions(candidateBase, key, mid, messages, out, accumulator);
        return;
      } catch (Exception e) {
        // try next candidate
      }
    }
  }

  private void streamChatCompletions(String urlBase, String apiKey, String model,
                                     List<Map<String, String>> messages,
                                     java.io.OutputStream out,
                                     StringBuilder accumulator) throws Exception {
    String url = urlBase + "/chat/completions";
    String json = om.writeValueAsString(
        java.util.Map.of("model", model, "messages", messages, "stream", true));

    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(120))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .header("HTTP-Referer", "https://kb-assistant.codec.com")
        .header("X-Title", "KB Assistant")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();

    HttpResponse<java.io.InputStream> resp = http.send(req,
        HttpResponse.BodyHandlers.ofInputStream());
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new RuntimeException("streamChat HTTP " + resp.statusCode());
    }

    try (java.io.BufferedReader reader = new java.io.BufferedReader(
        new java.io.InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
      java.io.PrintWriter writer = new java.io.PrintWriter(
          new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), false);
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("data:")) continue;
        String payload = line.substring(5).trim();
        if ("[DONE]".equals(payload)) break;
        try {
          JsonNode root = om.readTree(payload);
          JsonNode delta = root.path("choices").path(0).path("delta").path("content");
          if (delta.isTextual()) {
            String token = delta.asText();
            accumulator.append(token);
            // Write SSE token event
            for (String tokenLine : token.replace("\r", "").split("\n", -1)) {
              writer.print("event: token\n");
              writer.print("data: " + tokenLine + "\n");
              writer.print("\n");
            }
            writer.flush();
            out.flush();
          }
        } catch (Exception ignored) {}
      }
    }
  }

  private String callChatCompletions(String urlBase, String apiKey, String model,
                                     List<Map<String, String>> messages) throws Exception {
    String url = urlBase + "/chat/completions";
    String json = om.writeValueAsString(Map.of("model", model, "messages", messages));

    HttpResponse<String> resp = sendJson(url, apiKey, json);
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new RuntimeException("chat/completions HTTP " + resp.statusCode()
          + ": " + truncate(resp.body(), 300));
    }

    String body = resp.body();
    String trimmed = body == null ? "" : body.stripLeading();
    if (trimmed.startsWith("<")) {
      throw new RuntimeException("chat/completions returned HTML instead of JSON, please verify the provider baseUrl points to an API endpoint such as /v1");
    }

    JsonNode root = om.readTree(body);
    JsonNode choices = root.path("choices");
    if (!choices.isArray() || choices.isEmpty()) {
      throw new RuntimeException("chat/completions bad response: missing choices");
    }
    JsonNode msg = choices.get(0).path("message");
    JsonNode content = msg.path("content");
    String text = readContentText(content);
    if (text == null || text.isBlank()) {
      throw new RuntimeException("chat/completions bad response: missing content");
    }
    return text;
  }

  private HttpResponse<String> sendJson(String url, String apiKey, String json) throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(90))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .header("HTTP-Referer", "https://kb-assistant.codec.com")
        .header("X-Title", "KB Assistant")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }

  private static String readContentText(JsonNode content) {
    if (content == null || content.isMissingNode() || content.isNull()) return null;
    if (content.isTextual()) return content.asText();
    if (content.isArray()) {
      StringBuilder sb = new StringBuilder();
      for (JsonNode item : content) {
        if (item == null || item.isNull()) continue;
        JsonNode text = item.path("text");
        if (text.isTextual()) {
          if (!sb.isEmpty()) sb.append('\n');
          sb.append(text.asText());
        }
      }
      return sb.isEmpty() ? null : sb.toString();
    }
    return null;
  }

  static String systemRoleForModel(String model) {
    return prefersDeveloperRole(model) ? "developer" : "system";
  }

  private static boolean prefersDeveloperRole(String model) {
    String id = lower(model);
    return id.contains("gpt-5") || containsToken(id, "o1", "o3", "o4");
  }

  private static boolean containsToken(String value, String... needles) {
    String src = lower(value);
    if (src.isBlank()) return false;
    for (String needle : needles) {
      String n = lower(needle);
      if (n.isBlank()) continue;
      if (java.util.regex.Pattern.compile(
          "(^|[^a-z0-9])" + java.util.regex.Pattern.quote(n) + "([^a-z0-9]|$)")
          .matcher(src).find()) {
        return true;
      }
    }
    return false;
  }

  private static String lower(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private static String trimSlash(String s) {
    if (s == null) return "";
    String out = s.trim();
    while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
    return out;
  }

  private static List<String> candidateBaseUrls(String baseUrl) {
    String base = trimSlash(baseUrl);
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (!base.isBlank() && !base.endsWith("/v1") && !base.contains("/v1/")) {
      out.add(base + "/v1");
    }
    if (!base.isBlank()) out.add(base);
    return new ArrayList<>(out);
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max) + "...";
  }
}
