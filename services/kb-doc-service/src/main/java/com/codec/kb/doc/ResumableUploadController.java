package com.codec.kb.doc;

import com.codec.kb.common.DocumentDto;
import com.codec.kb.common.ChunkDto;
import com.codec.kb.common.UpsertRequest;
import com.codec.kb.common.util.HashUtil;
import com.codec.kb.common.util.IdUtil;
import com.codec.kb.common.util.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class ResumableUploadController {
  private static final int CHUNK_SIZE = 4 * 1024 * 1024; // 4MB
  private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");

  private final DocServiceConfig cfg;
  private final ResumableUploadStore store;
  private final IndexClient index;
  private final ParserRegistry parsers;
  private final Chunker chunker = new Chunker(900, 120);

  public ResumableUploadController(DocServiceConfig cfg, ResumableUploadStore store,
      IndexClient index, ParserRegistry parsers) {
    this.cfg = cfg;
    this.store = store;
    this.index = index;
    this.parsers = parsers;
  }

  /** Init upload: POST /internal/kbs/{kbId}/uploads
   * Body: { filename, totalSize, contentType } */
  @PostMapping("/internal/kbs/{kbId}/uploads")
  public Map<String, Object> initUpload(
      @PathVariable("kbId") String kbId,
      @RequestBody Map<String, Object> body) throws IOException {
    String id = WebUtils.safeKbDir(kbId);
    String filename = sanitize(getString(body, "filename", "upload.bin"));
    long totalSize = getLong(body, "totalSize", 0L);
    String contentType = getString(body, "contentType", "");
    if (totalSize <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "totalSize required");

    String uploadId = UUID.randomUUID().toString();
    store.initUpload(cfg.dataDir(), id, uploadId, filename, totalSize, contentType);

    return Map.of(
        "uploadId", uploadId,
        "chunkSize", CHUNK_SIZE,
        "filename", filename,
        "totalSize", totalSize,
        "status", "IN_PROGRESS"
    );
  }

  /** Upload chunk: PATCH /internal/kbs/{kbId}/uploads/{uploadId}
   * Header: Content-Range: bytes start-end/total */
  @PatchMapping("/internal/kbs/{kbId}/uploads/{uploadId}")
  public Map<String, Object> uploadChunk(
      @PathVariable("kbId") String kbId,
      @PathVariable("uploadId") String uploadId,
      @RequestBody byte[] data,
      HttpServletRequest request) throws IOException {
    String id = WebUtils.safeKbDir(kbId);
    ResumableUploadStore.UploadMeta existing;
    try {
      existing = store.getMeta(cfg.dataDir(), id, uploadId);
    } catch (IOException e) {
      throw mapUploadAccessException(e);
    }

    ParsedContentRange contentRange = parseContentRange(request.getHeader("Content-Range"), data, existing.totalSize());
    ResumableUploadStore.ApplyChunkResult result;
    try {
      result = store.applyChunk(cfg.dataDir(), id, uploadId, contentRange.start(), data);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    } catch (IOException e) {
      throw mapUploadAccessException(e);
    }
    ResumableUploadStore.UploadMeta meta = result.meta();

    if (result.completedNow()) {
      // Trigger ingestion
      try {
        ingestCompleted(id, uploadId, meta);
      } catch (Exception e) {
        return Map.of("status", "INGEST_ERROR", "error", e.getMessage() == null ? "unknown" : e.getMessage(),
            "received", meta.received(), "total", meta.totalSize());
      }
    }

    return Map.of(
        "uploadId", uploadId,
        "received", meta.received(),
        "total", meta.totalSize(),
        "status", meta.status()
    );
  }

  /** Get upload status: GET /internal/kbs/{kbId}/uploads/{uploadId} */
  @GetMapping("/internal/kbs/{kbId}/uploads/{uploadId}")
  public Map<String, Object> getStatus(
      @PathVariable("kbId") String kbId,
      @PathVariable("uploadId") String uploadId) throws IOException {
    String id = WebUtils.safeKbDir(kbId);
    try {
      ResumableUploadStore.UploadMeta meta = store.getMeta(cfg.dataDir(), id, uploadId);
      return Map.of(
          "uploadId", uploadId,
          "received", meta.received(),
          "total", meta.totalSize(),
          "status", meta.status()
      );
    } catch (IOException e) {
      throw mapUploadAccessException(e);
    }
  }

  private void ingestCompleted(String kbId, String uploadId, ResumableUploadStore.UploadMeta meta) throws IOException {
    Path dataFile = store.getDataFile(cfg.dataDir(), kbId, uploadId);

    String docId = IdUtil.newId("doc");
    String filename = meta.filename();
    Path base = Path.of(cfg.dataDir()).toAbsolutePath().normalize();
    Path dir = base.resolve("uploads").resolve(kbId).resolve(docId);
    Files.createDirectories(dir);
    Path saved = dir.resolve(filename);
    Files.copy(dataFile, saved, StandardCopyOption.REPLACE_EXISTING);

    long size = Files.size(saved);
    String sha = HashUtil.sha256Hex(Files.readAllBytes(saved));
    Instant now = Instant.now();

    DocumentDto doc = new DocumentDto(
        docId, filename, meta.contentType(), size, sha,
        now.toString(), now.toString(), "", List.of());

    ArrayList<ChunkDto> chunks = parseAndChunkOrThrow(saved, meta.contentType(), docId, filename);
    index.upsert(kbId, new UpsertRequest(doc, chunks));
  }

  private ArrayList<ChunkDto> parseAndChunkOrThrow(
      Path saved,
      String contentType,
      String docId,
      String filename) throws IOException {
    final ParsedDocument parsed;
    try {
      parsed = parsers.parseOrNull(saved, contentType);
    } catch (Exception e) {
      Files.deleteIfExists(saved);
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "document parse failed", e);
    }

    String text = (parsed == null) ? "" : parsed.text();
    List<String> pieces = chunker.chunk(text);
    ArrayList<ChunkDto> chunks = new ArrayList<>();
    for (int i = 0; i < pieces.size(); i++) {
      String chunkId = IdUtil.newId("chk");
      chunks.add(new ChunkDto(chunkId, docId, i, pieces.get(i), filename + "#chunk=" + i));
    }
    return chunks;
  }


  private static String sanitize(String filename) {
    if (filename == null || filename.isBlank()) return "upload.bin";
    String s = filename.replace('\\', '_').replace('/', '_');
    s = s.replaceAll("[\\r\\n\\t]", "_");
    s = s.replaceAll("[:*?\"<>|]", "_");
    if (s.length() > 120) s = s.substring(s.length() - 120);
    return s;
  }

  private static String getString(Map<String, Object> map, String key, String def) {
    Object v = map.get(key);
    if (v == null) return def;
    String s = v.toString().trim();
    return s.isBlank() ? def : s;
  }

  private static long getLong(Map<String, Object> map, String key, long def) {
    Object v = map.get(key);
    if (v == null) return def;
    try {
      return Long.parseLong(v.toString().trim());
    } catch (Exception e) {
      return def;
    }
  }

  private ParsedContentRange parseContentRange(String header, byte[] data, long expectedTotal) {
    if (header == null || header.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content-Range required");
    }
    Matcher matcher = CONTENT_RANGE_PATTERN.matcher(header.trim());
    if (!matcher.matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid Content-Range");
    }

    long start;
    long end;
    long total;
    try {
      start = Long.parseLong(matcher.group(1));
      end = Long.parseLong(matcher.group(2));
      total = Long.parseLong(matcher.group(3));
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid Content-Range");
    }
    if (start > end || total <= 0 || end >= total || total != expectedTotal) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid Content-Range");
    }

    long expectedLength = end - start + 1;
    int actualLength = data == null ? 0 : data.length;
    if (expectedLength != actualLength) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content-Range length mismatch");
    }
    return new ParsedContentRange(start, end, total);
  }

  private ResponseStatusException mapUploadAccessException(IOException e) {
    if (e instanceof ResumableUploadStore.LegacyUploadStateException) {
      return new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "upload not found");
  }

  private record ParsedContentRange(long start, long end, long total) {}
}
