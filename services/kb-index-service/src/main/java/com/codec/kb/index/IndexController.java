package com.codec.kb.index;

import com.codec.kb.common.SearchResponse;
import com.codec.kb.common.SearchRequest;
import com.codec.kb.common.DocumentDto;
import com.codec.kb.common.UpsertRequest;
import com.codec.kb.common.UpsertResponse;
import com.codec.kb.common.KnowledgeBaseDto;
import com.codec.kb.common.CreateKnowledgeBaseRequest;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class IndexController {
  private final IndexService svc;

  public IndexController(IndexService svc) {
    this.svc = svc;
  }

  @GetMapping("/internal/health")
  public Object health() {
    return Map.of("status", "ok", "time", Instant.now().toString());
  }

  @PostMapping(path = "/internal/upsert", consumes = MediaType.APPLICATION_JSON_VALUE)
  public UpsertResponse upsert(@RequestBody UpsertRequest req) {
    svc.upsert(req);
    return new UpsertResponse(true);
  }

  @GetMapping("/internal/kbs")
  public Map<String, Object> listKbs() {
    return Map.of("kbs", svc.listKbs());
  }

  @PostMapping(path = "/internal/kbs", consumes = MediaType.APPLICATION_JSON_VALUE)
  public KnowledgeBaseDto createKb(@RequestBody CreateKnowledgeBaseRequest req) {
    return svc.createKb(req);
  }

  @PatchMapping(path = "/internal/kbs/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public KnowledgeBaseDto updateKb(@PathVariable("id") String id, @RequestBody CreateKnowledgeBaseRequest req) {
    return svc.updateKb(id, req);
  }

  @DeleteMapping("/internal/kbs/{id}")
  public Map<String, Object> deleteKb(@PathVariable("id") String id) {
    boolean ok = svc.deleteKb(id);
    return Map.of("ok", ok);
  }

  @PostMapping(path = "/internal/kbs/{kbId}/upsert", consumes = MediaType.APPLICATION_JSON_VALUE)
  public UpsertResponse upsert(@PathVariable("kbId") String kbId, @RequestBody UpsertRequest req) {
    svc.upsert(kbId, req);
    return new UpsertResponse(true);
  }

  @GetMapping("/internal/documents")
  public Map<String, Object> listDocs() {
    List<DocumentDto> docs = svc.listDocuments();
    return Map.of("documents", docs);
  }

  @GetMapping("/internal/kbs/{kbId}/documents")
  public Map<String, Object> listDocs(@PathVariable("kbId") String kbId) {
    List<DocumentDto> docs = svc.listDocuments(kbId);
    return Map.of("documents", docs);
  }

  @GetMapping("/internal/documents/{id}")
  public Object getDoc(@PathVariable("id") String id) {
    Map<String, Object> doc = svc.getDocument(id);
    if (doc == null) return Map.of("error", "not_found");
    return doc;
  }

  @GetMapping("/internal/kbs/{kbId}/documents/{id}")
  public Object getDoc(@PathVariable("kbId") String kbId, @PathVariable("id") String id) {
    Map<String, Object> doc = svc.getDocument(kbId, id);
    if (doc == null) return Map.of("error", "not_found");
    return doc;
  }

  @DeleteMapping("/internal/documents/{id}")
  public Map<String, Object> deleteDoc(@PathVariable("id") String id) {
    boolean ok = svc.deleteDocument(id);
    return Map.of("ok", ok);
  }

  @DeleteMapping("/internal/kbs/{kbId}/documents/{id}")
  public Map<String, Object> deleteDoc(@PathVariable("kbId") String kbId, @PathVariable("id") String id) {
    boolean ok = svc.deleteDocument(kbId, id);
    return Map.of("ok", ok);
  }

  @PostMapping("/internal/reindex")
  public Map<String, Object> reindex() {
    svc.reindexAllEmbeddings();
    return Map.of("ok", true);
  }

  @PostMapping("/internal/kbs/{kbId}/reindex")
  public Map<String, Object> reindex(@PathVariable("kbId") String kbId) {
    svc.reindexAllEmbeddings(kbId);
    return Map.of("ok", true);
  }

  @GetMapping("/internal/stats")
  public Map<String, Object> stats() {
    return svc.stats();
  }

  @GetMapping("/internal/kbs/{kbId}/stats")
  public Map<String, Object> stats(@PathVariable("kbId") String kbId) {
    return svc.stats(kbId);
  }

  @PostMapping(path = "/internal/search", consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponse search(@RequestBody SearchRequest req) {
    return svc.search(req);
  }

  @PostMapping(path = "/internal/kbs/{kbId}/search", consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponse search(@PathVariable("kbId") String kbId, @RequestBody SearchRequest req) {
    return svc.search(kbId, req);
  }

  @GetMapping("/internal/chunks/{id}")
  public Object getChunk(
      @PathVariable("id") String id,
      @RequestParam(name = "kbId", required = false) String kbId) {
    Map<String, Object> r = svc.getChunk(kbId == null ? "" : kbId, id);
    if (r == null) return Map.of("error", "not_found");
    return r;
  }

  @GetMapping("/internal/kbs/{kbId}/chunks/{id}")
  public Object getChunkInKb(@PathVariable("kbId") String kbId, @PathVariable("id") String id) {
    Map<String, Object> r = svc.getChunk(kbId, id);
    if (r == null) return Map.of("error", "not_found");
    return r;
  }
}
