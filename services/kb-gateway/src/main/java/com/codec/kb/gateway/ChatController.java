package com.codec.kb.gateway;

import com.codec.kb.common.ChatResponse;
import com.codec.kb.common.ChatRequest;
import com.codec.kb.common.LlmConfig;
import com.codec.kb.common.util.WebUtils;
import com.codec.kb.gateway.auth.AuthUtil;
import com.codec.kb.gateway.clients.DownstreamClientSupport;
import com.codec.kb.gateway.kb.UserKbProvisioningService;
import com.codec.kb.gateway.models.AiModelResolverService;
import com.codec.kb.gateway.store.KbSettingsEntity;
import com.codec.kb.gateway.store.KbSettingsRepository;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ChatController {
  private static final String INTERNAL_HEADER = "X-Internal-Token";
  private static final String USER_HEADER = "X-User-Id";

  private final GatewayConfig cfg;
  private final RestClient rc;
  private final KbSettingsRepository kbSettings;
  private final UserKbProvisioningService provisioning;
  private final AiModelResolverService modelResolver;

  public ChatController(
      GatewayConfig cfg,
      RestClient rc,
      KbSettingsRepository kbSettings,
      UserKbProvisioningService provisioning,
      AiModelResolverService modelResolver
  ) {
    this.cfg = cfg;
    this.rc = rc;
    this.kbSettings = kbSettings;
    this.provisioning = provisioning;
    this.modelResolver = modelResolver;
  }

  @PostMapping(path = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ChatResponse chat(@RequestBody ChatRequest req) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String userId = uid.toString();

    List<String> kbIds = resolveChatKbIds(uid, req == null ? null : req.kbIds());
    Map<String, Integer> kbTopK = resolveKbTopK(kbIds);
    String model = req == null ? "" : WebUtils.safeTrim(req.model());
    LlmConfig llm = modelResolver.resolveLlm(model);
    if (!model.isBlank() && llm == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前所选模型不可用于对话，请改选已启用的对话模型。");
    }
    Boolean appendUser = req == null ? null : req.appendUser();

    String sid = req == null ? "" : req.sessionId();
    String msg = req == null ? "" : req.message();
    int fallbackTopK = req == null ? UserKbProvisioningService.DEFAULT_DOCUMENT_COUNT : Math.max(1, req.topK());
    int contextSize = req == null ? 0 : Math.max(0, req.contextSize());
    ChatRequest internalReq = new ChatRequest(sid, msg, fallbackTopK, kbIds, kbTopK, model, llm, appendUser, contextSize);
    try {
      return rc.post()
          .uri(cfg.aiUrl() + "/internal/chat")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .header(USER_HEADER, userId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(internalReq)
          .retrieve()
          .body(ChatResponse.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "对话服务错误");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "对话服务不可用");
    }
  }

  /**
   * True streaming endpoint: resolves KB IDs and model, builds the internal chat request,
   * then proxies the upstream SSE stream from the AI service directly to the client.
   * The AI service emits "token" events for each delta chunk and a final "meta" event.
   */
  @GetMapping(path = "/api/chat/stream", produces = "text/event-stream")
  public void chatStream(
      @RequestParam(name = "sessionId", defaultValue = "") String sessionId,
      @RequestParam(name = "message") String message,
      @RequestParam(name = "topK", defaultValue = "6") int topK,
      @RequestParam(name = "kbs", defaultValue = "") String kbs,
      @RequestParam(name = "useKb", defaultValue = "true") boolean useKb,
      @RequestParam(name = "model", defaultValue = "") String model,
      @RequestParam(name = "appendUser", defaultValue = "true") boolean appendUser,
      @RequestParam(name = "contextSize", defaultValue = "10") int contextSize,
      HttpServletResponse resp) throws IOException {

    UUID uid = AuthUtil.requirePrincipal().id();
    String userId = uid.toString();

    List<String> parsedKbIds = parseKbIds(kbs);
    List<String> requestedKbIds;
    if (!useKb) {
      requestedKbIds = List.of();
    } else {
      requestedKbIds = (kbs == null || kbs.trim().isEmpty()) ? null : parsedKbIds;
    }

    List<String> kbIds = resolveChatKbIds(uid, requestedKbIds);
    Map<String, Integer> kbTopK = resolveKbTopK(kbIds);
    String resolvedModel = WebUtils.safeTrim(model);
    LlmConfig llm = modelResolver.resolveLlm(resolvedModel);
    if (!resolvedModel.isBlank() && llm == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前所选模型不可用于对话，请改选已启用的对话模型。");
    }

    ChatRequest internalReq = new ChatRequest(
        sessionId, message, topK, kbIds, kbTopK, resolvedModel, llm, appendUser, Math.max(0, contextSize));

    try {
      rc.post()
          .uri(cfg.aiUrl() + "/internal/chat/stream")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .header(USER_HEADER, userId)
          .contentType(MediaType.APPLICATION_JSON)
          .body(internalReq)
          .exchange((req2, upstreamResp) -> {
            HttpStatusCode statusCode = upstreamResp.getStatusCode();
            if (statusCode.is2xxSuccessful()) {
              resp.setStatus(200);
              resp.setContentType("text/event-stream;charset=UTF-8");
              resp.setHeader("Cache-Control", "no-cache");
              try {
                upstreamResp.getBody().transferTo(resp.getOutputStream());
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            } else {
              byte[] bytes;
              try {
                bytes = upstreamResp.getBody().readAllBytes();
              } catch (IOException e) {
                bytes = new byte[0];
              }
              throw DownstreamClientSupport.translateRaw(
                  statusCode,
                  new String(bytes, StandardCharsets.UTF_8),
                  "对话服务错误");
            }
            return null;
          });
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "对话服务错误");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "对话服务不可用");
    }
  }

  // ─── Private helpers ───────────────────────────────────────────────────────

  private static List<String> parseKbIds(String csv) {
    if (csv == null) return List.of();
    String s = csv.trim();
    if (s.isEmpty()) return List.of();
    String[] parts = s.split("[,，;；\\s]+");
    ArrayList<String> out = new ArrayList<>();
    for (String p : parts) {
      String id = WebUtils.safeKbId(p);
      if (id.isEmpty()) continue;
      if (!out.contains(id)) out.add(id);
      if (out.size() >= 20) break;
    }
    return out;
  }

  private List<String> resolveChatKbIds(UUID userId, List<String> requestedKbIds) {
    if (userId == null) return List.of();

    LinkedHashSet<String> accessible = new LinkedHashSet<>(provisioning.listAccessibleKbIds(userId));
    if (accessible.isEmpty()) return List.of();

    if (requestedKbIds == null) {
      return List.copyOf(accessible);
    }
    if (requestedKbIds.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String kbId : requestedKbIds) {
      String id = WebUtils.safeKbId(kbId);
      if (id.isEmpty()) continue;
      if (accessible.contains(id)) out.add(id);
    }

    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private Map<String, Integer> resolveKbTopK(List<String> kbIds) {
    LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
    if (kbIds == null) return out;
    for (String kbId : kbIds) {
      if (kbId == null || kbId.isBlank()) continue;
      int dc = kbSettings.findById(kbId).map(KbSettingsEntity::getDocumentCount)
          .orElse(UserKbProvisioningService.DEFAULT_DOCUMENT_COUNT);
      out.put(kbId, WebUtils.clamp(dc, 1, 50));
    }
    return out;
  }
}
