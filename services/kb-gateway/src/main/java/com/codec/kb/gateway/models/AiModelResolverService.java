package com.codec.kb.gateway.models;

import com.codec.kb.common.LlmConfig;
import com.codec.kb.gateway.crypto.CryptoService;
import com.codec.kb.gateway.store.AiModelEntity;
import com.codec.kb.gateway.store.AiModelRepository;
import com.codec.kb.gateway.store.AiProviderEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AiModelResolverService {
  public record ModelEndpointConfig(
      String providerName,
      String baseUrl,
      String apiKey,
      String modelId
  ) {}

  private final AiModelRepository models;
  private final CryptoService crypto;

  public AiModelResolverService(AiModelRepository models, CryptoService crypto) {
    this.models = models;
    this.crypto = crypto;
  }

  @Transactional(readOnly = true)
  public LlmConfig resolveLlm(String modelSelector) {
    ModelEndpointConfig cfg = resolveModelConfig(modelSelector);
    if (cfg == null) return null;
    if (!isChatCapable(cfg)) return null;
    return new LlmConfig(cfg.baseUrl(), cfg.apiKey(), cfg.modelId());
  }

  @Transactional(readOnly = true)
  public ModelEndpointConfig resolveModelConfig(String modelSelector) {
    UUID id = parseUuidOrNull(modelSelector);
    if (id == null) return null;

    AiModelEntity m = models.findById(id).orElse(null);
    if (m == null || !m.isEnabled()) return null;

    AiProviderEntity p = m.getProvider();
    if (p == null || !p.isEnabled()) return null;

    String apiKey = crypto.decrypt(p.getApiKeyEnc());
    if (apiKey == null || apiKey.isBlank()) return null;

    String baseUrl = normalizeBaseUrl(p.getBaseUrl());

    String modelId = m.getModelId() == null ? "" : m.getModelId().trim();
    if (baseUrl.isBlank() || modelId.isBlank()) return null;

    String providerName = p.getName() == null ? "" : p.getName().trim();
    return new ModelEndpointConfig(providerName, baseUrl, apiKey, modelId);
  }

  private static String normalizeBaseUrl(String baseUrl) {
    String s = baseUrl == null ? "" : baseUrl.trim();
    if (s.isBlank()) return "";
    while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
    if (s.endsWith("/chat/completions")) s = s.substring(0, s.length() - "/chat/completions".length());
    if (s.endsWith("/responses")) s = s.substring(0, s.length() - "/responses".length());
    if (s.endsWith("/embeddings")) s = s.substring(0, s.length() - "/embeddings".length());
    if (s.endsWith("/models")) s = s.substring(0, s.length() - "/models".length());
    if (s.endsWith("/api/tags")) s = s.substring(0, s.length() - "/api/tags".length());
    while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
    if (s.endsWith("/api")) s = s + "/v1";
    if (!s.startsWith("http://") && !s.startsWith("https://")) return "";
    return s;
  }

  private static boolean isChatCapable(ModelEndpointConfig cfg) {
    if (cfg == null) return false;
    String haystack = (cfg.modelId() == null ? "" : cfg.modelId()) + " "
        + (cfg.providerName() == null ? "" : cfg.providerName());
    return !ModelTagSupport.isEmbeddingModel(haystack) && !ModelTagSupport.isRerankModel(haystack);
  }

  private static UUID parseUuidOrNull(String s) {
    try {
      return UUID.fromString(s == null ? "" : s.trim());
    } catch (Exception ignored) {
      return null;
    }
  }
}
