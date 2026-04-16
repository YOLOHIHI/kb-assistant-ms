package com.codec.kb.index;

import com.codec.kb.common.util.WebUtils;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Encapsulates the embedding strategy selection logic so that IndexService does not
 * need to know the details of which embedding back-end to use for a given KB.
 * Strategy is chosen based on the KB's {@code embeddingMode}:
 * <ul>
 *   <li>{@code local} — uses the local in-process embedding model</li>
 *   <li>{@code api} with a UUID model ref — delegates to the managed embedding gateway</li>
 *   <li>{@code api} with a plain model name — calls the configured embedding API directly</li>
 * </ul>
 */
@Service
public class EmbeddingFacade {
  private final IndexServiceConfig cfg;
  private final KbJpaRepository kbRepo;
  private final LocalEmbeddingBackend localEmbed;
  private final EmbeddingApiClient apiEmbed;
  private final ManagedEmbeddingClient managedEmbed;

  public EmbeddingFacade(
      IndexServiceConfig cfg,
      KbJpaRepository kbRepo,
      LocalEmbeddingBackend localEmbed,
      EmbeddingApiClient apiEmbed,
      ManagedEmbeddingClient managedEmbed
  ) {
    this.cfg = cfg;
    this.kbRepo = kbRepo;
    this.localEmbed = localEmbed;
    this.apiEmbed = apiEmbed;
    this.managedEmbed = managedEmbed;
  }

  /**
   * Embeds the given texts using the strategy configured for the specified KB.
   */
  public List<float[]> embed(String kbId, List<String> texts) {
    KbEntity kb = kbRepo.findById(kbId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown kbId: " + kbId));
    String mode = WebUtils.safeTrim(kb.getEmbeddingMode()).toLowerCase();
    if ("api".equals(mode)) {
      String model = WebUtils.safeTrim(kb.getEmbeddingModel());
      if (model.isBlank()) throw new IllegalStateException("KB " + kbId + " missing embeddingModel");
      String baseUrl = WebUtils.safeTrim(kb.getEmbeddingBaseUrl());
      if (baseUrl.isBlank() && isUuid(model)) {
        return managedEmbed.embed(model, texts);
      }
      if (baseUrl.isBlank()) baseUrl = WebUtils.safeTrim(cfg.embedApiBaseUrl());
      String apiKey = WebUtils.safeTrim(cfg.embedApiKey());
      if (baseUrl.isBlank() || apiKey.isBlank()) {
        throw new IllegalStateException("Embedding API not configured.");
      }
      return apiEmbed.embed(baseUrl, apiKey, model, texts);
    }
    return localEmbed.embed(texts);
  }

  private static boolean isUuid(String value) {
    try { UUID.fromString(WebUtils.safeTrim(value)); return true; } catch (Exception e) { return false; }
  }
}
