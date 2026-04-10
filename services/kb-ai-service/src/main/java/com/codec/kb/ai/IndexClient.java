package com.codec.kb.ai;

import com.codec.kb.common.SearchResponse;
import com.codec.kb.common.SearchRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public final class IndexClient {
  private static final Logger log = LoggerFactory.getLogger(IndexClient.class);
  private final AiServiceConfig cfg;
  private final RestClient rc;

  public IndexClient(AiServiceConfig cfg, RestClient.Builder restClientBuilder) {
    this.cfg = cfg;
    this.rc = restClientBuilder.build();
  }

  public SearchResponse search(List<String> kbIds, String query, int topK) {
    SearchRequest req = new SearchRequest(query, topK, kbIds);
    return rc.post()
        .uri(cfg.indexUrl() + "/internal/search")
        .header("X-Internal-Token", cfg.internalToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(req)
        .retrieve()
        .body(SearchResponse.class);
  }

  public SearchResponse search(String kbId, String query, int topK) {
    String id = kbId == null ? "" : kbId.trim();
    if (id.isBlank()) id = "default";

    SearchRequest req = new SearchRequest(query, topK, null);
    String url = cfg.indexUrl() + "/internal/kbs/" + id + "/search";
    try {
      return rc.post()
          .uri(url)
          .header("X-Internal-Token", cfg.internalToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(req)
          .retrieve()
          .body(SearchResponse.class);
    } catch (Exception e) {
      log.warn("Index search request failed: url={}, kbId={}, error={}", url, id, e.getMessage());
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getChunk(String kbId, String chunkId) {
    String id = kbId == null ? "" : kbId.trim();
    String url = id.isBlank()
        ? (cfg.indexUrl() + "/internal/chunks/" + chunkId)
        : (cfg.indexUrl() + "/internal/kbs/" + id + "/chunks/" + chunkId);
    return (Map<String, Object>) rc.get()
        .uri(url)
        .header("X-Internal-Token", cfg.internalToken())
        .retrieve()
        .body(Map.class);
  }
}
