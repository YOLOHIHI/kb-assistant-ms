package com.codec.kb.gateway.admin;

import com.codec.kb.gateway.store.AiModelEntity;
import com.codec.kb.gateway.store.AiModelRepository;
import com.codec.kb.gateway.store.AiProviderEntity;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Coordinates syncing remote provider model lists into the local database.
 * Uses {@link ProviderCatalogClient} for fetching and {@link ModelPayloadParser}
 * for extracting model fields.
 */
@Service
public class ProviderModelSyncService {

  private final ProviderCatalogClient catalog;
  private final ModelPayloadParser parser;
  private final AiModelRepository models;

  public record SyncStats(int created, int updated) {}

  public ProviderModelSyncService(ProviderCatalogClient catalog, ModelPayloadParser parser,
      AiModelRepository models) {
    this.catalog = catalog;
    this.parser = parser;
    this.models = models;
  }

  /**
   * Fetches the remote model list for the given provider and syncs it into the DB.
   */
  public SyncStats sync(AiProviderEntity p) {
    List<JsonNode> remote = catalog.fetchProviderModelItems(p);
    return syncItems(p, remote);
  }

  /**
   * Syncs a pre-fetched list of model-item nodes into the database.
   * Use this overload when you already have the remote items (avoids a second fetch).
   */
  public SyncStats syncItems(AiProviderEntity p, List<JsonNode> remote) {
    int created = 0;
    int updated = 0;

    for (JsonNode item : remote) {
      String modelId = parser.extractModelId(item);
      if (modelId.isBlank()) continue;
      String displayName = ModelPayloadParser.safeDisplayName(parser.extractDisplayName(item, modelId));

      Optional<AiModelEntity> existing = models.findByProvider_IdAndModelId(p.getId(), modelId);
      if (existing.isPresent()) {
        AiModelEntity e = existing.get();
        if (ModelPayloadParser.shouldUpdateDisplayName(e.getDisplayName(), e.getModelId(), displayName)) {
          e.setDisplayName(displayName);
          models.save(e);
          updated++;
        }
        continue;
      }

      AiModelEntity e = new AiModelEntity();
      e.setProvider(p);
      e.setModelId(modelId);
      e.setDisplayName(displayName.isBlank() ? modelId : displayName);
      e.setEnabled(false);
      models.save(e);
      created++;
    }

    return new SyncStats(created, updated);
  }
}
