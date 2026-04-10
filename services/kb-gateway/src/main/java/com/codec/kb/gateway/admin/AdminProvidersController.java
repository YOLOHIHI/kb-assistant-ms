package com.codec.kb.gateway.admin;

import com.codec.kb.gateway.crypto.CryptoService;
import com.codec.kb.gateway.store.AiProviderEntity;
import com.codec.kb.gateway.store.AiProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/providers")
@Validated
public class AdminProvidersController {

  private final AiProviderRepository providers;
  private final CryptoService crypto;
  private final ProviderCatalogClient catalog;
  private final ProviderModelSyncService syncService;

  public AdminProvidersController(
      AiProviderRepository providers,
      CryptoService crypto,
      ProviderCatalogClient catalog,
      ProviderModelSyncService syncService
  ) {
    this.providers = providers;
    this.crypto = crypto;
    this.catalog = catalog;
    this.syncService = syncService;
  }

  // ─── CRUD ────────────────────────────────────────────────────────────────────

  @GetMapping
  public Map<String, Object> list() {
    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (AiProviderEntity p : providers.findAllByOrderByNameAsc()) {
      out.add(toDto(p));
    }
    return Map.of("providers", out);
  }

  public record CreateProviderRequest(
      @NotBlank String name,
      @NotBlank String baseUrl,
      @NotBlank String apiKey,
      Boolean enabled
  ) {}

  @PostMapping
  public Map<String, Object> create(@RequestBody CreateProviderRequest req) {
    String name = safeName(req == null ? null : req.name());
    String baseUrl = normalizeBaseUrl(req == null ? null : req.baseUrl());
    String apiKey = safeApiKey(req == null ? null : req.apiKey());

    AiProviderEntity p = new AiProviderEntity();
    p.setName(name);
    p.setBaseUrl(baseUrl);
    p.setApiKeyEnc(crypto.encrypt(apiKey));
    p.setEnabled(req != null && req.enabled() != null ? req.enabled() : true);
    providers.save(p);
    return toDto(p);
  }

  public record UpdateProviderRequest(
      String name,
      String baseUrl,
      String apiKey,
      Boolean enabled
  ) {}

  @PatchMapping("/{id}")
  public Map<String, Object> update(@PathVariable("id") String id, @RequestBody UpdateProviderRequest req) {
    UUID pid = parseUuid(id);
    AiProviderEntity p = providers.findById(pid)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

    if (req != null && req.name() != null && !req.name().trim().isBlank()) {
      p.setName(safeName(req.name()));
    }
    if (req != null && req.baseUrl() != null && !req.baseUrl().trim().isBlank()) {
      p.setBaseUrl(normalizeBaseUrl(req.baseUrl()));
    }
    if (req != null && req.apiKey() != null && !req.apiKey().trim().isBlank()) {
      p.setApiKeyEnc(crypto.encrypt(safeApiKey(req.apiKey())));
    }
    if (req != null && req.enabled() != null) {
      p.setEnabled(req.enabled());
    }

    providers.save(p);
    return toDto(p);
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> delete(@PathVariable("id") String id) {
    UUID pid = parseUuid(id);
    if (!providers.existsById(pid)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
    providers.deleteById(pid);
    return Map.of("ok", true);
  }

  // ─── Model fetch / sync endpoints ────────────────────────────────────────────

  @GetMapping("/{id}/openrouter/models")
  public Map<String, Object> fetchOpenRouterModels(
      @PathVariable("id") String id,
      @RequestParam(name = "sync", defaultValue = "false") boolean sync
  ) {
    UUID pid = parseUuid(id);
    AiProviderEntity p = providers.findById(pid)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
    try {
      List<JsonNode> remote = catalog.fetchProviderModelItems(p);
      ProviderModelSyncService.SyncStats stats = sync
          ? syncService.syncItems(p, remote)
          : new ProviderModelSyncService.SyncStats(0, 0);

      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      out.put("models", catalog.buildProviderModelRows(p, remote));
      if (sync) {
        out.put("created", stats.created());
        out.put("updated", stats.updated());
      }
      return out;
    } catch (ResponseStatusException e) {
      List<Map<String, Object>> local = catalog.buildLocalModelRows(p);
      LinkedHashMap<String, Object> out = new LinkedHashMap<>();
      out.put("models", local);
      out.put("fallback", true);
      out.put("notice", local.isEmpty()
          ? "远端模型列表加载失败，当前渠道未获取到可展示模型。"
          : "远端模型列表加载失败，已回退到本地已同步模型。");
      out.put("upstreamError", ModelPayloadParser.safeTrim(e.getReason()));
      return out;
    }
  }

  @PostMapping("/{id}/openrouter/sync-models")
  public Map<String, Object> syncOpenRouterModels(@PathVariable("id") String id) {
    UUID pid = parseUuid(id);
    AiProviderEntity p = providers.findById(pid)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

    List<JsonNode> remote = catalog.fetchProviderModelItems(p);
    ProviderModelSyncService.SyncStats stats = syncService.syncItems(p, remote);

    return Map.of(
        "ok", true,
        "totalRemote", remote.size(),
        "created", stats.created(),
        "updated", stats.updated()
    );
  }

  // ─── DTO mapping ─────────────────────────────────────────────────────────────

  private Map<String, Object> toDto(AiProviderEntity p) {
    String apiKey = crypto.decrypt(p == null ? null : p.getApiKeyEnc());
    return Map.of(
        "id", String.valueOf(p.getId()),
        "name", p.getName(),
        "baseUrl", p.getBaseUrl(),
        "enabled", p.isEnabled(),
        "apiKeyMasked", maskKey(apiKey),
        "createdAt", p.getCreatedAt() == null ? "" : p.getCreatedAt().toString(),
        "updatedAt", p.getUpdatedAt() == null ? "" : p.getUpdatedAt().toString()
    );
  }

  // ─── Request validation helpers ───────────────────────────────────────────────

  private static UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
    }
  }

  private static String safeName(String name) {
    String s = name == null ? "" : name.trim();
    if (s.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    if (s.length() > 64) s = s.substring(0, 64);
    return s;
  }

  private static String normalizeBaseUrl(String baseUrl) {
    String s = baseUrl == null ? "" : baseUrl.trim();
    if (s.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl is required");
    if (s.length() > 240) s = s.substring(0, 240);
    while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
    if (s.endsWith("/chat/completions")) s = s.substring(0, s.length() - "/chat/completions".length());
    if (s.endsWith("/responses")) s = s.substring(0, s.length() - "/responses".length());
    if (s.endsWith("/embeddings")) s = s.substring(0, s.length() - "/embeddings".length());
    if (s.endsWith("/models")) s = s.substring(0, s.length() - "/models".length());
    if (s.endsWith("/api/tags")) s = s.substring(0, s.length() - "/api/tags".length());
    while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
    if (s.endsWith("/api")) s = s + "/v1";
    if (!s.startsWith("http://") && !s.startsWith("https://")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl must start with http(s)://");
    }
    return s;
  }

  private static String safeApiKey(String apiKey) {
    String s = apiKey == null ? "" : apiKey.trim();
    if (s.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey is required");
    if (s.length() > 300) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey too long");
    return s;
  }

  private static String maskKey(String apiKey) {
    String s = apiKey == null ? "" : apiKey.trim();
    if (s.isBlank()) return "";
    String last4 = s.length() <= 4 ? s : s.substring(s.length() - 4);
    return "****" + last4;
  }
}
