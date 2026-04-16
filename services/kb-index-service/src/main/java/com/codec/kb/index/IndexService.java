package com.codec.kb.index;

import com.codec.kb.common.SearchResponse;
import com.codec.kb.common.SearchRequest;
import com.codec.kb.common.SearchHit;
import com.codec.kb.common.DocumentDto;
import com.codec.kb.common.ChunkDto;
import com.codec.kb.common.UpsertRequest;
import com.codec.kb.common.KnowledgeBaseDto;
import com.codec.kb.common.CreateKnowledgeBaseRequest;
import com.codec.kb.common.util.WebUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IndexService {
  private static final Logger log = LoggerFactory.getLogger(IndexService.class);
  public static final String DEFAULT_KB_ID = "default";
  public static final String DEFAULT_KB_NAME = "公司章程";

  private record KbSpec(String name, String mode, String model, String baseUrl) {}

  private final KbJpaRepository kbRepo;
  private final DocumentJpaRepository docRepo;
  private final ChunkJpaRepository chunkRepo;
  private final EmbeddingFacade embeddingFacade;
  private final PgvectorStatusService pgvectorStatus;
  private final IndexTuning tuning;
  private final FloatArrayConverter vecConv = new FloatArrayConverter();

  // In-memory BM25 index per KB (rebuilt from DB on demand).
  // Dense search uses pgvector directly via ChunkJpaRepository.
  private final ConcurrentHashMap<String, HybridIndex> bm25Cache = new ConcurrentHashMap<>();

  public IndexService(
      KbJpaRepository kbRepo,
      DocumentJpaRepository docRepo,
      ChunkJpaRepository chunkRepo,
      EmbeddingFacade embeddingFacade,
      IndexTuning tuning,
      PgvectorStatusService pgvectorStatus) {
    this.kbRepo = kbRepo;
    this.docRepo = docRepo;
    this.chunkRepo = chunkRepo;
    this.embeddingFacade = embeddingFacade;
    this.tuning = tuning;
    this.pgvectorStatus = pgvectorStatus;
    ensureDefaultKbExists();
  }

  // ─── KB management ────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<KnowledgeBaseDto> listKbs() {
    return kbRepo.findAll().stream().map(this::toKbDto).toList();
  }

  @Transactional
  public KnowledgeBaseDto createKb(CreateKnowledgeBaseRequest req) {
    KbSpec spec = normalizeKbSpec(req);
    String id = newKbId();
    Instant now = Instant.now();
    KbEntity e = new KbEntity();
    e.setId(id);
    e.setName(spec.name());
    e.setEmbeddingMode(spec.mode());
    e.setEmbeddingModel(spec.model().isBlank() ? null : spec.model());
    e.setEmbeddingBaseUrl(spec.baseUrl().isBlank() ? null : spec.baseUrl());
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    kbRepo.save(e);
    return toKbDto(e);
  }

  @Transactional
  public KnowledgeBaseDto updateKb(String kbId, CreateKnowledgeBaseRequest req) {
    String id = WebUtils.safeTrim(kbId);
    if (id.isBlank()) throw new IllegalArgumentException("kbId is required");
    KbEntity e = kbRepo.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Unknown kbId: " + kbId));
    KbSpec spec = normalizeKbSpec(req);
    e.setName(spec.name());
    e.setEmbeddingMode(spec.mode());
    e.setEmbeddingModel(spec.model().isBlank() ? null : spec.model());
    e.setEmbeddingBaseUrl(spec.baseUrl().isBlank() ? null : spec.baseUrl());
    e.setUpdatedAt(Instant.now());
    kbRepo.save(e);
    return toKbDto(e);
  }

  @Transactional
  public boolean deleteKb(String kbId) {
    String id = WebUtils.safeTrim(kbId);
    if (id.isBlank() || DEFAULT_KB_ID.equals(id)) return false;
    if (!kbRepo.existsById(id)) return false;
    if (kbRepo.count() <= 1) return false;

    // cascade: delete chunks and documents belong to this KB
    chunkRepo.deleteByKbId(id);
    docRepo.deleteByKbId(id);
    kbRepo.deleteById(id);
    bm25Cache.remove(id);
    return true;
  }

  // ─── Document / chunk upsert ───────────────────────────────────────────────

  @Transactional
  public void upsert(String kbId, UpsertRequest req) {
    String id = normalizeKbId(kbId);
    String docId = req.document().id();

    // Compute embeddings before touching persisted state so failures do not leave
    // a partial write path behind.
    List<String> texts = req.chunks().stream().map(ChunkDto::text).toList();
    List<float[]> vecs = embedForWrite(id, texts);

    // Remove old data for this document
    chunkRepo.deleteByDocId(docId);
    docRepo.deleteById(docId);

    // Persist document
    DocumentEntity docE = toDocEntity(req.document(), id);
    docRepo.save(docE);

    // Persist chunks
    List<ChunkEntity> chunkEntities = new ArrayList<>();
    for (int i = 0; i < req.chunks().size(); i++) {
      ChunkDto c = req.chunks().get(i);
      ChunkEntity ce = new ChunkEntity();
      ce.setId(c.id());
      ce.setDocId(docId);
      ce.setKbId(id);
      ce.setChunkIndex(c.index());
      ce.setContent(c.text());
      ce.setSourceHint(c.sourceHint());
      ce.setEmbedding(vecs.get(i));
      ce.setCreatedAt(Instant.now());
      chunkEntities.add(ce);
    }
    chunkRepo.saveAll(chunkEntities);
    long chunksWithEmbedding = countNonNullEmbeddings(vecs);
    log.info(
        "Upserted document docId={} into kbId={} chunks={} chunksWithEmbedding={}",
        docId,
        id,
        req.chunks().size(),
        chunksWithEmbedding
    );

    // Invalidate BM25 cache; will be rebuilt lazily on next search
    bm25Cache.remove(id);
  }

  @Transactional
  public void upsert(UpsertRequest req) {
    upsert(DEFAULT_KB_ID, req);
  }

  // ─── Document deletion ─────────────────────────────────────────────────────

  @Transactional
  public boolean deleteDocument(String kbId, String docId) {
    String id = normalizeKbId(kbId);
    DocumentEntity doc = docRepo.findById(docId).orElse(null);
    if (doc == null) return false;
    if (!id.equals(doc.getKbId())) return false; // prevent cross-KB deletion
    chunkRepo.deleteByDocId(docId);
    docRepo.deleteById(docId);
    bm25Cache.remove(id);
    return true;
  }

  @Transactional
  public boolean deleteDocument(String docId) {
    // Find the KB this document belongs to
    return docRepo.findById(docId).map(d -> deleteDocument(d.getKbId(), docId)).orElse(false);
  }

  // ─── Reindex ──────────────────────────────────────────────────────────────

  @Transactional
  public Map<String, Object> reindexAllEmbeddings(String kbId) {
    String id = normalizeKbId(kbId);
    List<ChunkEntity> chunks = chunkRepo.findByKbId(id);
    log.info("Reindex started for kbId={} chunks={}", id, chunks.size());
    if (chunks.isEmpty()) {
      log.info("Reindex skipped for kbId={}, no chunks found", id);
      return reindexResult(id, 0, 0);
    }
    List<String> texts = chunks.stream().map(ChunkEntity::getContent).toList();
    List<float[]> vecs = embedForKb(id, texts);
    for (int i = 0; i < chunks.size(); i++) {
      chunks.get(i).setEmbedding(vecs.get(i));
    }
    chunkRepo.saveAll(chunks);
    bm25Cache.remove(id);
    long chunksWithEmbedding = countNonNullEmbeddings(vecs);
    log.info(
        "Reindex finished for kbId={} chunks={} chunksWithEmbedding={}",
        id,
        chunks.size(),
        chunksWithEmbedding
    );
    return reindexResult(id, chunks.size(), chunksWithEmbedding);
  }

  @Transactional
  public Map<String, Object> reindexAllEmbeddings() {
    int kbCount = 0;
    long chunkCount = 0L;
    long chunksWithEmbedding = 0L;
    for (KbEntity kb : kbRepo.findAll()) {
      if (kb == null) continue;
      String kbId = kb.getId();
      if (kbId == null || kbId.isBlank()) continue;
      Map<String, Object> result = reindexAllEmbeddings(kbId);
      kbCount++;
      chunkCount += numberValue(result.get("chunks"));
      chunksWithEmbedding += numberValue(result.get("chunksWithEmbedding"));
    }
    PgvectorStatusService.Status status = pgvectorStatus.currentStatus();
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("knowledgeBases", kbCount);
    out.put("chunks", chunkCount);
    out.put("chunksWithEmbedding", chunksWithEmbedding);
    out.put("chunksWithoutEmbedding", Math.max(0L, chunkCount - chunksWithEmbedding));
    out.put("pgvectorInstalled", status.pgvectorInstalled());
    out.put("embeddingColumnType", status.embeddingColumnType());
    out.put("denseRetrievalAvailable", status.denseRetrievalAvailable());
    out.put("updatedAt", Instant.now().toString());
    return out;
  }

  // ─── Queries ───────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<DocumentDto> listDocuments(String kbId) {
    return docRepo.findByKbId(normalizeKbId(kbId)).stream().map(this::toDocDto).toList();
  }

  @Transactional(readOnly = true)
  public List<DocumentDto> listDocuments() {
    return listDocuments(DEFAULT_KB_ID);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getDocument(String kbId, String id) {
    return docRepo.findById(id).map(d -> {
      List<ChunkDto> chunks = chunkRepo.findByDocId(id)
          .stream().map(this::toChunkDto).toList();
      return (Map<String, Object>) Map.of("document", toDocDto(d), "chunks", chunks);
    }).orElse(null);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getDocument(String id) {
    return getDocument(DEFAULT_KB_ID, id);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getChunk(String kbId, String chunkId) {
    if (kbId == null || kbId.isBlank()) {
      // search across all KBs
      return chunkRepo.findById(chunkId).map(c -> {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("chunk", toChunkDto(c));
        out.put("document", docRepo.findById(c.getDocId()).map(this::toDocDto).orElse(null));
        return out;
      }).orElse(null);
    }
    return chunkRepo.findById(chunkId)
        .filter(c -> kbId.equals(c.getKbId()))
        .map(c -> {
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("chunk", toChunkDto(c));
          out.put("document", docRepo.findById(c.getDocId()).map(this::toDocDto).orElse(null));
          return out;
        }).orElse(null);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getChunk(String chunkId) {
    return getChunk("", chunkId);
  }

  // ─── Search ────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
  public SearchResponse search(SearchRequest req) {
    String q = req.query() == null ? "" : req.query().trim();
    int topK = Math.max(1, req.topK());
    if (q.isBlank()) return new SearchResponse(List.of());

    List<String> kbIds = normalizeKbIds(req.kbIds());
    ArrayList<SearchHit> merged = new ArrayList<>();
    for (String kbId : kbIds) {
      merged.addAll(searchInKb(kbId, q, topK));
    }
    merged.sort((a, b) -> Double.compare(b.score(), a.score()));
    if (merged.size() > topK) merged = new ArrayList<>(merged.subList(0, topK));
    return new SearchResponse(merged);
  }

  @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
  public SearchResponse search(String kbId, SearchRequest req) {
    String q = req == null || req.query() == null ? "" : req.query().trim();
    int topK = req == null ? 6 : Math.max(1, req.topK());
    if (q.isBlank()) return new SearchResponse(List.of());
    return new SearchResponse(searchInKb(normalizeKbId(kbId), q, topK));
  }

  private List<SearchHit> searchInKb(String kbId, String query, int topK) {
    // ── Dense search via native pgvector storage ───────────────────────────────
    LinkedHashMap<String, Double> denseScores = new LinkedHashMap<>();
    LinkedHashMap<String, ChunkDto> denseChunks = new LinkedHashMap<>();
    PgvectorStatusService.Status pgvector = pgvectorStatus.currentStatus();
    if (pgvector.denseRetrievalAvailable()) {
      try {
        float[] qvec = embedForKb(kbId, List.of(query)).get(0);
        String vecStr = vecConv.convertToDatabaseColumn(qvec);
        List<Object[]> denseRows = chunkRepo.findTopKByDenseSimilarity(kbId, vecStr, topK * 2);
        for (Object[] row : denseRows) {
          String cid = String.valueOf(row[0]);
          double score = row[6] instanceof Number ? ((Number) row[6]).doubleValue() : 0.0;
          denseScores.put(cid, score);
          denseChunks.put(cid, rowToChunkDto(row));
        }
      } catch (Exception e) {
        // Native vector retrieval or embedding unavailable — fall back to BM25
        log.debug("Dense search skipped for kbId={}: {}", kbId, e.getMessage());
      }
    } else {
      log.debug("Dense search disabled for kbId={} because native pgvector storage is unavailable", kbId);
    }

    // ── BM25 search (in-memory, rebuilt lazily from DB text) ───────────────
    LinkedHashMap<String, Double> bm25Scores = new LinkedHashMap<>();
    try {
      HybridIndex bm25 = bm25Cache.computeIfAbsent(kbId, this::buildBm25Index);
      // Pass null queryVec so HybridIndex uses only BM25 scoring (wDense=0)
      List<SearchHit> bm25Hits = bm25.search(kbId, query, null, topK * 2);
      for (SearchHit h : bm25Hits) bm25Scores.put(h.chunk().id(), h.score());
    } catch (Exception e) {
      log.warn("BM25 search failed for kbId={}: {}", kbId, e.getMessage());
    }

    // ── Fuse scores ─────────────────────────────────────────────────────────
    double wBm25 = tuning.hybrid().bm25Weight();
    double wDense = tuning.hybrid().denseWeight();

    // Normalize BM25 scores
    double maxBm25 = bm25Scores.values().stream().mapToDouble(d -> d).max().orElse(1.0);
    if (maxBm25 <= 0) maxBm25 = 1.0;
    // Dense scores from pgvector are already cosine similarity [0,1]

    LinkedHashMap<String, Double> fused = new LinkedHashMap<>();
    Set<String> allIds = new LinkedHashSet<>();
    allIds.addAll(denseScores.keySet());
    allIds.addAll(bm25Scores.keySet());
    for (String cid : allIds) {
      double d = denseScores.getOrDefault(cid, 0.0);
      double b = bm25Scores.getOrDefault(cid, 0.0) / maxBm25;
      fused.put(cid, wDense * d + wBm25 * b);
    }

    // Sort and return topK
    ArrayList<Map.Entry<String, Double>> sorted = new ArrayList<>(fused.entrySet());
    sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

    ArrayList<SearchHit> result = new ArrayList<>();
    for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
      String cid = sorted.get(i).getKey();
      double score = sorted.get(i).getValue();
      try {
        ChunkDto chunk = denseChunks.containsKey(cid)
            ? denseChunks.get(cid)
            : chunkRepo.findById(cid).map(this::toChunkDto).orElse(null);
        if (chunk != null) result.add(new SearchHit(chunk, score, kbId));
      } catch (Exception e) {
        log.debug("Failed to load chunk {}: {}", cid, e.getMessage());
      }
    }
    return result;
  }

  // ─── Stats ─────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Map<String, Object> stats(String kbId) {
    String id = normalizeKbId(kbId);
    KbEntity kb = kbRepo.findById(id).orElse(null);
    long docs = docRepo.countByKbId(id);
    long chunks = chunkRepo.countByKbId(id);
    long chunksWithEmbedding = chunkRepo.countByKbIdAndEmbeddingIsNotNull(id);
    long sizeBytes = docRepo.sumSizeBytesByKbId(id);
    PgvectorStatusService.Status status = pgvectorStatus.currentStatus();

    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("kbId", id);
    out.put("documents", docs);
    out.put("chunks", chunks);
    out.put("chunksWithEmbedding", chunksWithEmbedding);
    out.put("chunksWithoutEmbedding", Math.max(0L, chunks - chunksWithEmbedding));
    out.put("sizeBytes", sizeBytes);
    out.put("embeddingMode", kb == null ? "" : blankIfNull(kb.getEmbeddingMode()));
    out.put("embeddingModel", kb == null ? "" : blankIfNull(kb.getEmbeddingModel()));
    out.put("pgvectorInstalled", status.pgvectorInstalled());
    out.put("embeddingColumnType", status.embeddingColumnType());
    out.put("denseRetrievalAvailable", status.denseRetrievalAvailable());
    out.put("retrievalMode", status.denseRetrievalAvailable() ? "hybrid" : "bm25-only");
    out.put("updatedAt", Instant.now().toString());
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> stats() {
    return stats(DEFAULT_KB_ID);
  }

  // ─── Internal helpers ──────────────────────────────────────────────────────

  private void ensureDefaultKbExists() {
    if (!kbRepo.existsById(DEFAULT_KB_ID)) {
      Instant now = Instant.now();
      KbEntity e = new KbEntity();
      e.setId(DEFAULT_KB_ID);
      e.setName(DEFAULT_KB_NAME);
      e.setEmbeddingMode("local");
      e.setCreatedAt(now);
      e.setUpdatedAt(now);
      kbRepo.save(e);
    } else {
      kbRepo.findById(DEFAULT_KB_ID).ifPresent(e -> {
        if (!DEFAULT_KB_NAME.equals(e.getName())) {
          e.setName(DEFAULT_KB_NAME);
          e.setUpdatedAt(Instant.now());
          kbRepo.save(e);
        }
      });
    }
  }

  private HybridIndex buildBm25Index(String kbId) {
    List<ChunkEntity> chunks = chunkRepo.findByKbId(kbId);
    List<DocumentDto> docs = docRepo.findByKbId(kbId).stream()
        .map(this::toDocDto).toList();
    List<ChunkDto> chunkDtos = chunks.stream().map(this::toChunkDto).toList();
    // Build with wDense=0 so HybridIndex.search() returns pure BM25 scores
    HybridIndex idx = new HybridIndex(
        tuning.bm25().k1(), tuning.bm25().b(), 1.0, 0.0);
    idx.rebuild(docs, chunkDtos, Map.of());
    return idx;
  }

  private List<float[]> embedForKb(String kbId, List<String> texts) {
    return embeddingFacade.embed(kbId, texts);
  }

  private List<float[]> embedForWrite(String kbId, List<String> texts) {
    if (texts == null || texts.isEmpty()) return List.of();
    List<float[]> vecs;
    try {
      vecs = embeddingFacade.embed(kbId, texts);
    } catch (Exception e) {
      throw new IllegalStateException("Embedding write failed for kbId=" + kbId + ": " + e.getMessage(), e);
    }
    if (vecs == null || vecs.size() != texts.size()) {
      throw new IllegalStateException("Embedding write failed for kbId=" + kbId + ": embedding count mismatch");
    }
    return vecs;
  }

  private Map<String, Object> reindexResult(String kbId, long chunks, long chunksWithEmbedding) {
    PgvectorStatusService.Status status = pgvectorStatus.currentStatus();
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("kbId", kbId);
    out.put("chunks", chunks);
    out.put("chunksWithEmbedding", chunksWithEmbedding);
    out.put("chunksWithoutEmbedding", Math.max(0L, chunks - chunksWithEmbedding));
    out.put("pgvectorInstalled", status.pgvectorInstalled());
    out.put("embeddingColumnType", status.embeddingColumnType());
    out.put("denseRetrievalAvailable", status.denseRetrievalAvailable());
    out.put("updatedAt", Instant.now().toString());
    return out;
  }

  private static long countNonNullEmbeddings(List<float[]> vecs) {
    long count = 0L;
    if (vecs == null) return 0L;
    for (float[] vec : vecs) {
      if (vec != null) count++;
    }
    return count;
  }

  private static long numberValue(Object value) {
    return value instanceof Number n ? n.longValue() : 0L;
  }

  private static String blankIfNull(String value) {
    return value == null ? "" : value;
  }

  // ─── Converters ────────────────────────────────────────────────────────────

  private KnowledgeBaseDto toKbDto(KbEntity e) {
    return new KnowledgeBaseDto(
        e.getId(), e.getName(), e.getEmbeddingMode(),
        e.getEmbeddingModel(), e.getEmbeddingBaseUrl(),
        e.getCreatedAt().toString(), e.getUpdatedAt().toString()
    );
  }

  private DocumentDto toDocDto(DocumentEntity e) {
    List<String> tags = null;
    if (e.getTags() != null && !e.getTags().isBlank()) {
      try {
        // parse simple JSON array like ["a","b"]
        String raw = e.getTags().trim();
        if (raw.startsWith("[") && raw.endsWith("]")) {
          raw = raw.substring(1, raw.length() - 1);
          tags = Arrays.stream(raw.split(","))
              .map(s -> s.trim().replaceAll("^\"|\"$", ""))
              .filter(s -> !s.isBlank())
              .toList();
        }
      } catch (Exception ignored) {}
    }
    return new DocumentDto(
        e.getId(), e.getFilename(), e.getContentType(), e.getSizeBytes(),
        e.getSha256(), e.getCreatedAt().toString(), e.getUpdatedAt().toString(),
        e.getCategory(), tags
    );
  }

  private ChunkDto toChunkDto(ChunkEntity e) {
    return new ChunkDto(e.getId(), e.getDocId(), e.getChunkIndex(),
        e.getContent(), e.getSourceHint());
  }

  private DocumentEntity toDocEntity(DocumentDto d, String kbId) {
    DocumentEntity e = new DocumentEntity();
    e.setId(d.id());
    e.setKbId(kbId);
    e.setFilename(d.filename() == null ? "" : d.filename());
    e.setContentType(d.contentType());
    e.setSizeBytes(d.sizeBytes());
    e.setSha256(d.sha256());
    e.setCategory(d.category());
    if (d.tags() != null && !d.tags().isEmpty()) {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < d.tags().size(); i++) {
        if (i > 0) sb.append(',');
        sb.append('"').append(d.tags().get(i).replace("\"", "\\\"")).append('"');
      }
      sb.append(']');
      e.setTags(sb.toString());
    }
    Instant now = Instant.now();
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    return e;
  }

  private static ChunkDto rowToChunkDto(Object[] row) {
    String id = String.valueOf(row[0]);
    String docId = String.valueOf(row[1]);
    int chunkIdx = row[3] instanceof Number ? ((Number) row[3]).intValue() : 0;
    String content = row[4] == null ? "" : String.valueOf(row[4]);
    String sourceHint = row[5] == null ? null : String.valueOf(row[5]);
    return new ChunkDto(id, docId, chunkIdx, content, sourceHint);
  }

  private List<String> normalizeKbIds(List<String> kbIds) {
    if (kbIds == null || kbIds.isEmpty()) return List.of(DEFAULT_KB_ID);
    Set<String> known = new LinkedHashSet<>();
    kbRepo.findAll().forEach(kb -> known.add(kb.getId()));
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String s : kbIds) {
      String id = WebUtils.safeTrim(s);
      if (!id.isBlank() && known.contains(id)) out.add(id);
    }
    if (out.isEmpty()) out.add(DEFAULT_KB_ID);
    return List.copyOf(out);
  }

  private static String normalizeKbId(String kbId) {
    String s = WebUtils.safeTrim(kbId);
    return s.isBlank() ? DEFAULT_KB_ID : s;
  }

  private String newKbId() {
    for (int i = 0; i < 20; i++) {
      String id = "kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
      if (!kbRepo.existsById(id)) return id;
    }
    return "kb_" + UUID.randomUUID().toString().replace("-", "");
  }

  private static KbSpec normalizeKbSpec(CreateKnowledgeBaseRequest req) {
    String name = req == null ? "" : WebUtils.safeTrim(req.name());
    if (name.isBlank()) throw new IllegalArgumentException("name is required");
    if (name.length() > 64) name = name.substring(0, 64);
    String mode = req == null ? "" : WebUtils.safeTrim(req.embeddingMode());
    if (mode.isBlank()) mode = "local";
    mode = mode.toLowerCase();
    if (!mode.equals("local") && !mode.equals("api"))
      throw new IllegalArgumentException("embeddingMode must be local|api");
    String model = req == null ? "" : WebUtils.safeTrim(req.embeddingModel());
    String baseUrl = req == null ? "" : WebUtils.safeTrim(req.embeddingBaseUrl());
    if (mode.equals("api")) {
      if (model.isBlank()) throw new IllegalArgumentException("embeddingModel is required for api mode");
      if (model.length() > 120) model = model.substring(0, 120);
      if (baseUrl.length() > 240) baseUrl = baseUrl.substring(0, 240);
    } else {
      model = "";
      baseUrl = "";
    }
    return new KbSpec(name, mode, model, baseUrl);
  }
}
