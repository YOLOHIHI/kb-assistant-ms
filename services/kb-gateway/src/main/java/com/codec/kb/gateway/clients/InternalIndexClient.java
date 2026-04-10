package com.codec.kb.gateway.clients;

import com.codec.kb.common.KnowledgeBaseDto;
import com.codec.kb.common.CreateKnowledgeBaseRequest;
import com.codec.kb.gateway.GatewayConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public final class InternalIndexClient {
  private static final String INTERNAL_HEADER = "X-Internal-Token";

  private final GatewayConfig cfg;
  private final RestClient rc;
  private final ObjectMapper om;

  public InternalIndexClient(GatewayConfig cfg, RestClient rc, ObjectMapper om) {
    this.cfg = cfg;
    this.rc = rc;
    this.om = om;
  }

  @SuppressWarnings("unchecked")
  public List<KnowledgeBaseDto> listKbs() {
    Map<String, Object> r;
    try {
      r = (Map<String, Object>) rc.get()
          .uri(cfg.indexUrl() + "/internal/kbs")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "读取知识库列表失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
    Object kbs = r == null ? null : r.get("kbs");
    if (kbs == null) return List.of();
    try {
      return om.convertValue(kbs, new TypeReference<List<KnowledgeBaseDto>>() {});
    } catch (IllegalArgumentException ignored) {}
    return List.of();
  }

  public KnowledgeBaseDto createKb(CreateKnowledgeBaseRequest req) {
    try {
      return rc.post()
          .uri(cfg.indexUrl() + "/internal/kbs")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(req)
          .retrieve()
          .body(KnowledgeBaseDto.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "创建知识库失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
  }

  public KnowledgeBaseDto updateKb(String kbId, CreateKnowledgeBaseRequest req) {
    try {
      return rc.patch()
          .uri(cfg.indexUrl() + "/internal/kbs/" + kbId)
          .header(INTERNAL_HEADER, cfg.internalToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(req)
          .retrieve()
          .body(KnowledgeBaseDto.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "更新知识库失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
  }

  @SuppressWarnings("unchecked")
  public boolean deleteKb(String kbId) {
    Map<String, Object> r;
    try {
      r = (Map<String, Object>) rc.delete()
          .uri(cfg.indexUrl() + "/internal/kbs/" + kbId)
          .header(INTERNAL_HEADER, cfg.internalToken())
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "删除知识库失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
    Object ok = r == null ? null : r.get("ok");
    return ok instanceof Boolean b && b;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> listDocuments(String kbId) {
    try {
      return (Map<String, Object>) rc.get()
          .uri(cfg.indexUrl() + "/internal/kbs/" + kbId + "/documents")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "读取知识库文档失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> stats(String kbId) {
    try {
      return (Map<String, Object>) rc.get()
          .uri(cfg.indexUrl() + "/internal/kbs/" + kbId + "/stats")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "读取知识库统计失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> deleteDocument(String kbId, String docId) {
    try {
      return (Map<String, Object>) rc.delete()
          .uri(cfg.indexUrl() + "/internal/kbs/" + kbId + "/documents/" + docId)
          .header(INTERNAL_HEADER, cfg.internalToken())
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "删除文档失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getChunk(String kbId, String chunkId) {
    try {
      return (Map<String, Object>) rc.get()
          .uri(cfg.indexUrl() + "/internal/kbs/" + kbId + "/chunks/" + chunkId)
          .header(INTERNAL_HEADER, cfg.internalToken())
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "读取分块失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> reindexKb(String kbId) {
    try {
      return (Map<String, Object>) rc.post()
          .uri(cfg.indexUrl() + "/internal/kbs/" + kbId + "/reindex")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "重建索引失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "索引服务不可用");
    }
  }
}
