package com.codec.kb.ai;

import com.codec.kb.common.ChatResponse;
import com.codec.kb.common.ChatRequest;
import com.codec.kb.common.SearchResponse;
import com.codec.kb.common.SearchHit;
import com.codec.kb.common.CitationDto;
import com.codec.kb.common.LlmConfig;
import com.codec.kb.common.util.HashUtil;
import com.codec.kb.common.util.WebUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public final class RagService {
  private static final Logger log = LoggerFactory.getLogger(RagService.class);

  private static final int MAX_CONTEXT_CHUNKS = 80;
  private static final int MAX_CONTEXT_MESSAGES = 40;
  private static final int MAX_TEXT_PER_CHUNK = 1200;
  private static final int MAX_SNIPPET_LENGTH = 240;
  private static final int MAX_TITLE_LENGTH = 24;

  private final SiliconflowConfig sfConfig;
  private final SessionService sessions;
  private final IndexClient index;
  private final OpenAiChatClient openAi;

  public RagService(SiliconflowConfig sfConfig, SessionService sessions, IndexClient index, OpenAiChatClient openAi) {
    this.sfConfig = sfConfig;
    this.sessions = sessions;
    this.index = index;
    this.openAi = openAi;
  }

  public ChatResponse chat(String userId, ChatRequest req) {
    String msg = req.message() == null ? "" : req.message().trim();
    if (msg.isBlank()) throw new IllegalArgumentException("message is empty");

    boolean appendUserMessage = req.appendUser() == null || req.appendUser();

    String sid = req.sessionId() == null ? "" : req.sessionId().trim();
    ChatSession s = sid.isBlank() ? null : sessions.get(userId, sid);
    if (s == null) {
      s = sessions.create(userId, titleFrom(msg));
      appendUserMessage = true;
    }

    // user-controlled context window (0 = no history; N = last N messages)
    int contextSize = Math.max(0, Math.min(req.contextSize(), MAX_CONTEXT_MESSAGES));

    // fetch history BEFORE appending current user message
    List<ChatMessage> history = List.of();
    if (contextSize > 0) {
      List<ChatMessage> all = sessions.messages(userId, s.id());
      int from = Math.max(0, all.size() - contextSize);
      history = all.subList(from, all.size());
    }

    int fallbackTopK = req.topK() <= 0 ? 6 : req.topK();
    List<String> kbIds = req.kbIds() == null ? List.of() : req.kbIds();
    boolean kbEnabled = !kbIds.isEmpty();
    Map<String, Integer> topKByKb = req.kbTopK();

    SearchOutcome searchOutcome = kbEnabled
        ? searchKnowledge(kbIds, msg, fallbackTopK, topKByKb)
        : SearchOutcome.empty();
    ArrayList<SearchHit> merged = new ArrayList<>(searchOutcome.hits());

    ArrayList<CitationDto> cites = new ArrayList<>();
    for (SearchHit h : merged) {
      cites.add(new CitationDto(
          h.chunk().docId(), filenameFromHint(h.chunk().sourceHint()),
          h.chunk().id(), h.chunk().index(),
          h.chunk().sourceHint(), snippet(h.chunk().text(), MAX_SNIPPET_LENGTH), h.kbId()));
    }

    // resolve effective LLM: explicit req.llm() first, then SiliconFlow env-var fallback
    LlmConfig llmCfg = req.llm();
    String sfApiKey = sfConfig.apiKey() == null ? "" : sfConfig.apiKey().trim();
    String sfModel = sfConfig.model() == null ? "" : sfConfig.model().trim();
    String sfBaseUrl = sfConfig.baseUrl() == null ? "https://api.siliconflow.cn/v1" : sfConfig.baseUrl().trim();
    if (llmCfg == null && !sfApiKey.isBlank() && !sfModel.isBlank()) {
      llmCfg = new LlmConfig(sfBaseUrl, sfApiKey, sfModel);
    }

    String answer;
    if (!kbEnabled) {
      String text = callWithHistory(llmCfg, generalSystemPrompt(), msg, history);
      answer = (text == null || text.isBlank())
          ? (llmCfg != null ? "当前模型返回为空，请检查渠道配置或模型 ID。"
                            : "已关闭知识库检索，且当前没有可用模型，无法回答。")
          : text.trim();
    } else if (merged.isEmpty()) {
      answer = kbSearchUnavailableMessage(searchOutcome.allFailed());
    } else {
      // RAG: KB context injected into current user turn; history messages stay plain
      String text = callWithHistory(llmCfg, ragSystemPrompt(),
          buildUserPrompt(msg, merged), history);
      answer = (text == null || text.isBlank()) ? fallbackAnswer(msg, cites) : text.trim();
    }

    String answerHash = HashUtil.sha256Hex(answer.getBytes(StandardCharsets.UTF_8));

    if (appendUserMessage) sessions.appendMessage(s.id(), "USER", msg, null);
    sessions.appendMessage(s.id(), "ASSISTANT", answer,
        llmCfg != null ? llmCfg.model() : null);
    sessions.touch(userId, s.id());

    return new ChatResponse(s.id(), answer, answerHash, cites);
  }

  /**
   * Streaming variant of {@link #chat}: performs the same KB retrieval and prompt-building
   * steps synchronously, then proxies the LLM's SSE token stream directly to {@code out}.
   * After the stream ends, persists the session messages and writes a final "meta" SSE event.
   */
  public void chatStream(String userId, ChatRequest req, java.io.OutputStream out) throws java.io.IOException {
    String msg = req.message() == null ? "" : req.message().trim();
    if (msg.isBlank()) throw new IllegalArgumentException("message is empty");

    boolean appendUserMessage = req.appendUser() == null || req.appendUser();

    String sid = req.sessionId() == null ? "" : req.sessionId().trim();
    ChatSession s = sid.isBlank() ? null : sessions.get(userId, sid);
    if (s == null) {
      s = sessions.create(userId, titleFrom(msg));
      appendUserMessage = true;
    }

    int contextSize = Math.max(0, Math.min(req.contextSize(), MAX_CONTEXT_MESSAGES));
    List<ChatMessage> history = List.of();
    if (contextSize > 0) {
      List<ChatMessage> all = sessions.messages(userId, s.id());
      int from = Math.max(0, all.size() - contextSize);
      history = all.subList(from, all.size());
    }

    int fallbackTopK = req.topK() <= 0 ? 6 : req.topK();
    List<String> kbIds = req.kbIds() == null ? List.of() : req.kbIds();
    boolean kbEnabled = !kbIds.isEmpty();
    java.util.Map<String, Integer> topKByKb = req.kbTopK();

    SearchOutcome searchOutcome = kbEnabled
        ? searchKnowledge(kbIds, msg, fallbackTopK, topKByKb)
        : SearchOutcome.empty();
    java.util.ArrayList<SearchHit> merged = new java.util.ArrayList<>(searchOutcome.hits());

    java.util.ArrayList<CitationDto> cites = new java.util.ArrayList<>();
    for (SearchHit h : merged) {
      cites.add(new CitationDto(
          h.chunk().docId(), filenameFromHint(h.chunk().sourceHint()),
          h.chunk().id(), h.chunk().index(),
          h.chunk().sourceHint(), snippet(h.chunk().text(), MAX_SNIPPET_LENGTH), h.kbId()));
    }

    LlmConfig llmCfg = req.llm();
    String sfApiKey = sfConfig.apiKey() == null ? "" : sfConfig.apiKey().trim();
    String sfModel = sfConfig.model() == null ? "" : sfConfig.model().trim();
    String sfBaseUrl = sfConfig.baseUrl() == null ? "https://api.siliconflow.cn/v1" : sfConfig.baseUrl().trim();
    if (llmCfg == null && !sfApiKey.isBlank() && !sfModel.isBlank()) {
      llmCfg = new LlmConfig(sfBaseUrl, sfApiKey, sfModel);
    }

    String systemPrompt = (!kbEnabled || merged.isEmpty()) ? generalSystemPrompt() : ragSystemPrompt();
    String userPrompt = (!kbEnabled || merged.isEmpty()) ? msg : buildUserPrompt(msg, merged);

    StringBuilder accumulator = new StringBuilder();

    if (llmCfg != null) {
      java.util.ArrayList<java.util.Map<String, String>> messages = new java.util.ArrayList<>();
      if (!systemPrompt.isBlank()) {
        messages.add(java.util.Map.of("role", OpenAiChatClient.systemRoleForModel(llmCfg.model()), "content", systemPrompt));
      }
      for (ChatMessage m : history) {
        String role = "USER".equalsIgnoreCase(m.role()) ? "user" : "assistant";
        messages.add(java.util.Map.of("role", role, "content", m.content()));
      }
      messages.add(java.util.Map.of("role", "user", "content", userPrompt));
      try {
        openAi.streamChat(llmCfg.baseUrl(), llmCfg.apiKey(), llmCfg.model(), messages, out, accumulator);
      } catch (Exception e) {
        log.warn("streamChat failed: {}", e.getMessage(), e);
      }
    }

    String answer = accumulator.isEmpty()
        ? (kbEnabled
            ? (merged.isEmpty() ? kbSearchUnavailableMessage(searchOutcome.allFailed()) : fallbackAnswer(msg, cites))
            : "（模型返回为空）")
        : accumulator.toString().trim();
    String answerHash = HashUtil.sha256Hex(answer.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    final boolean doAppend = appendUserMessage;
    final ChatSession session = s;
    final LlmConfig finalLlm = llmCfg;
    if (doAppend) sessions.appendMessage(session.id(), "USER", msg, null);
    sessions.appendMessage(session.id(), "ASSISTANT", answer, finalLlm != null ? finalLlm.model() : null);
    sessions.touch(userId, session.id());

    // Write meta SSE event
    java.io.PrintWriter writer = new java.io.PrintWriter(
        new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), false);
    try {
      String metaJson = new com.fasterxml.jackson.databind.ObjectMapper()
          .writeValueAsString(java.util.Map.of(
              "sessionId", session.id(),
              "answerHash", answerHash,
              "citations", cites
          ));
      writer.print("event: meta\n");
      writer.print("data: " + metaJson + "\n");
      writer.print("\n");
      writer.flush();
      out.flush();
    } catch (Exception e) {
      log.warn("Failed to write meta event: {}", e.getMessage());
    }
  }

  /**
   * Calls the LLM with optional conversation history.
   * History messages are plain (no KB re-injection).
   * The current user message is appended as the last turn.
   */
  private String callWithHistory(LlmConfig llm, String systemPrompt,
                                 String currentUserMsg, List<ChatMessage> history) {
    if (llm == null) return null;
    try {
      if (history.isEmpty()) {
        return openAi.chat(llm.baseUrl(), llm.apiKey(), llm.model(),
            systemPrompt, currentUserMsg);
      }
      ArrayList<Map<String, String>> messages = new ArrayList<>();
      if (systemPrompt != null && !systemPrompt.isBlank()) {
        messages.add(Map.of("role", OpenAiChatClient.systemRoleForModel(llm.model()), "content", systemPrompt));
      }
      for (ChatMessage m : history) {
        String role = "USER".equalsIgnoreCase(m.role()) ? "user" : "assistant";
        messages.add(Map.of("role", role, "content", m.content()));
      }
      messages.add(Map.of("role", "user", "content", currentUserMsg));
      return openAi.chatWithMessages(llm.baseUrl(), llm.apiKey(), llm.model(), messages);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private static String ragSystemPrompt() {
    return "你是一个企业知识助手。请严格根据提供的知识片段回答用户问题。\n规则：\n"
        + "1) 只能使用知识片段中的信息，不得编造。\n"
        + "2) 如果片段不足以回答，请明确说明知识库暂无相关信息。\n"
        + "3) 回答中如引用片段内容，请用 [1][2] 这样的编号标注。\n";
  }

  private static String generalSystemPrompt() {
    return "你是一个智能问答助手。请直接回答用户问题，表达清晰、准确、简洁。";
  }

  private static String buildUserPrompt(String question, List<SearchHit> hits) {
    StringBuilder sb = new StringBuilder("知识片段：\n");
    for (int i = 0; i < hits.size(); i++) {
      SearchHit h = hits.get(i);
      String text = h.chunk().text();
      if (text.length() > MAX_TEXT_PER_CHUNK) text = text.substring(0, MAX_TEXT_PER_CHUNK) + "...";
      sb.append("[").append(i + 1).append("] ")
          .append(h.chunk().sourceHint() == null ? "" : h.chunk().sourceHint())
          .append("\n").append(text).append("\n\n");
    }
    sb.append("用户问题：").append(question).append("\n")
      .append("请给出简洁、准确的回答，并在需要时标注引用编号。\n");
    return sb.toString();
  }

  private SearchOutcome searchKnowledge(List<String> kbIds, String query, int fallbackTopK,
                                        Map<String, Integer> topKByKb) {
    log.debug("KB search: kbIds={}, query={}", kbIds, query);
    LinkedHashMap<String, SearchHit> dedup = new LinkedHashMap<>();
    int attempts = 0;
    int failed = 0;

    if (kbIds.size() > 1) {
      int batchTopK = 0;
      for (String kbId : kbIds) {
        int k = fallbackTopK;
        if (topKByKb != null && topKByKb.get(kbId) != null) k = topKByKb.get(kbId);
        batchTopK += WebUtils.clamp(k, 1, 50);
      }
      attempts++;
      try {
        appendHits(dedup, index.search(kbIds, query, WebUtils.clamp(batchTopK, 1, MAX_CONTEXT_CHUNKS)));
        List<SearchHit> hits = limitHits(dedup);
        if (hits.isEmpty()) {
          log.info("KB search returned 0 results: kbIds={}, failed={}/{}", kbIds, failed, attempts);
        }
        return new SearchOutcome(hits, false);
      } catch (Exception e) {
        failed++;
        log.warn("KB batch search failed for kbIds={}: {}", kbIds, e.getMessage(), e);
      }
    }

    for (String kbId : kbIds) {
      int k = fallbackTopK;
      if (topKByKb != null && topKByKb.get(kbId) != null) k = topKByKb.get(kbId);
      k = WebUtils.clamp(k, 1, 50);
      attempts++;
      try {
        appendHits(dedup, index.search(kbId, query, k));
      } catch (Exception e) {
        failed++;
        log.warn("KB search failed for kbId={}: {}", kbId, e.getMessage(), e);
      }
    }

    List<SearchHit> hits = limitHits(dedup);
    if (hits.isEmpty()) {
      log.info("KB search returned 0 results: kbIds={}, failed={}/{}", kbIds, failed, attempts);
    }
    return new SearchOutcome(hits, attempts > 0 && failed == attempts);
  }

  private static void appendHits(LinkedHashMap<String, SearchHit> dedup, SearchResponse sr) {
    if (sr == null || sr.hits() == null) return;
    for (SearchHit h : sr.hits()) {
      if (h == null || h.chunk() == null || h.chunk().id() == null || h.chunk().id().isBlank()) continue;
      String key = (h.kbId() == null ? "" : h.kbId()) + ":" + h.chunk().id();
      dedup.putIfAbsent(key, h);
    }
  }

  private static List<SearchHit> limitHits(LinkedHashMap<String, SearchHit> dedup) {
    ArrayList<SearchHit> hits = new ArrayList<>(dedup.values());
    if (hits.size() > MAX_CONTEXT_CHUNKS) {
      return new ArrayList<>(hits.subList(0, MAX_CONTEXT_CHUNKS));
    }
    return hits;
  }

  private static String kbSearchUnavailableMessage(boolean allFailed) {
    return allFailed
        ? "知识库检索服务暂时不可用，请稍后再试。"
        : "知识库中暂无相关信息。你可以换个关键词或上传相关文档后再试。";
  }

  private record SearchOutcome(List<SearchHit> hits, boolean allFailed) {
    private static SearchOutcome empty() {
      return new SearchOutcome(List.of(), false);
    }
  }

  private static String fallbackAnswer(String question, List<CitationDto> cites) {
    if (cites.isEmpty()) return "知识库中暂无相关信息。";
    StringBuilder sb = new StringBuilder("根据知识库检索结果：\n\n问题：")
        .append(question).append("\n\n要点：\n");
    for (int i = 0; i < cites.size(); i++) {
      CitationDto c = cites.get(i);
      sb.append("- [").append(i + 1).append("] ")
          .append(c.filename()).append("：").append(c.snippet()).append("\n");
    }
    sb.append("\n说明：当前为降级回答（未启用外部模型或模型调用失败）。\n");
    return sb.toString();
  }

  private static String filenameFromHint(String h) {
    if (h == null) return "";
    int idx = h.indexOf("#chunk=");
    return idx > 0 ? h.substring(0, idx) : h.trim();
  }

  private static String snippet(String text, int max) {
    if (text == null) return "";
    String s = text.replace("\n", " ").replaceAll("\\s+", " ").trim();
    return s.length() <= max ? s : s.substring(0, max).trim() + "...";
  }

  private static String titleFrom(String message) {
    String s = message.strip().replace("\n", " ").replaceAll("\\s+", " ");
    return s.length() > MAX_TITLE_LENGTH ? s.substring(0, MAX_TITLE_LENGTH) + "..." : s;
  }

}
