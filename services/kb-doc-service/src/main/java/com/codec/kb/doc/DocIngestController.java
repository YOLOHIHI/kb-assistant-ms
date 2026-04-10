package com.codec.kb.doc;

import com.codec.kb.common.DocumentDto;
import com.codec.kb.common.ChunkDto;
import com.codec.kb.common.UpsertRequest;
import com.codec.kb.common.util.HashUtil;
import com.codec.kb.common.util.IdUtil;
import com.codec.kb.common.util.WebUtils;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DocIngestController {
  private static final int CHUNK_SIZE = 900;
  private static final int CHUNK_OVERLAP = 120;
  private static final int MAX_FILENAME_LENGTH = 120;
  private static final int MAX_TAG_LENGTH = 40;
  private static final int MAX_TAGS_COUNT = 20;

  private final DocServiceConfig cfg;
  private final IndexClient index;
  private final ParserRegistry parsers;
  private final Chunker chunker = new Chunker(CHUNK_SIZE, CHUNK_OVERLAP);

  public DocIngestController(DocServiceConfig cfg, IndexClient index, ParserRegistry parsers) {
    this.cfg = cfg;
    this.index = index;
    this.parsers = parsers;
  }

  @GetMapping("/internal/health")
  public Object health() {
    return java.util.Map.of("status", "ok", "time", Instant.now().toString());
  }

  @PostMapping(path = "/internal/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentDto upload(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "kbId", required = false) String kbId,
      @RequestPart(value = "category", required = false) String category,
      @RequestPart(value = "tags", required = false) String tags
  ) throws IOException {
    String id = WebUtils.safeKbDir(kbId);
    return handleUpload(id, file, category, tags);
  }

  @PostMapping(path = "/internal/kbs/{kbId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentDto uploadToKb(
      @PathVariable("kbId") String kbId,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "category", required = false) String category,
      @RequestPart(value = "tags", required = false) String tags
  ) throws IOException {
    String id = WebUtils.safeKbDir(kbId);
    return handleUpload(id, file, category, tags);
  }

  @PostMapping(path = "/internal/kbs/{kbId}/documents/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public java.util.Map<String, Object> batchUpload(
      @PathVariable("kbId") String kbId,
      @RequestPart("files") List<MultipartFile> files
  ) {
    String id = WebUtils.safeKbDir(kbId);
    java.util.ArrayList<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) continue;
      try {
        DocumentDto doc = handleUpload(id, file, null, null);
        results.add(java.util.Map.of("docId", doc.id(), "filename", doc.filename(), "status", "ok"));
      } catch (Exception e) {
        results.add(java.util.Map.of(
            "filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
            "status", "error",
            "error", e.getMessage() == null ? "unknown" : e.getMessage()
        ));
      }
    }
    return java.util.Map.of("results", results);
  }

  @PostMapping(path = "/internal/kbs/{kbId}/documents/import-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public java.util.Map<String, Object> importZip(
      @PathVariable("kbId") String kbId,
      @RequestPart("file") MultipartFile file
  ) throws IOException {
    String id = WebUtils.safeKbDir(kbId);
    java.util.ArrayList<java.util.Map<String, Object>> results = new java.util.ArrayList<>();

    try (java.io.InputStream input = file.getInputStream();
         java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(input)) {
      java.util.zip.ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) { zis.closeEntry(); continue; }
        String entryName = entry.getName();
        if (entryName.contains("__MACOSX") || new java.io.File(entryName).getName().startsWith(".")) {
          zis.closeEntry(); continue;
        }
        String filename = sanitize(new java.io.File(entryName).getName());
        if (filename.isBlank() || filename.equals("upload.bin")) { zis.closeEntry(); continue; }

        final byte[] entryBytes = zis.readAllBytes();
        zis.closeEntry();

        // Save to disk and process
        String docId = IdUtil.newId("doc");
        Path base = Path.of(cfg.dataDir()).toAbsolutePath().normalize();
        Path dir = base.resolve("uploads").resolve(id).resolve(docId);
        Files.createDirectories(dir);
        Path saved = dir.resolve(filename);
        Files.write(saved, entryBytes);

        String sha = HashUtil.sha256Hex(entryBytes);
        Instant now = Instant.now();

        DocumentDto doc = new DocumentDto(
            docId, filename, "", entryBytes.length, sha,
            now.toString(), now.toString(), "", java.util.List.of());

        try {
          ParsedDocument parsed = parsers.parseOrNull(saved, "");
          String text = (parsed == null) ? "" : parsed.text();
          java.util.List<String> pieces = chunker.chunk(text);
          java.util.ArrayList<ChunkDto> chunks = new java.util.ArrayList<>();
          for (int i = 0; i < pieces.size(); i++) {
            String chunkId = IdUtil.newId("chk");
            chunks.add(new ChunkDto(chunkId, docId, i, pieces.get(i), filename + "#chunk=" + i));
          }
          index.upsert(id, new UpsertRequest(doc, chunks));
          results.add(java.util.Map.of("docId", docId, "filename", filename, "status", "ok"));
        } catch (Exception e) {
          results.add(java.util.Map.of("filename", filename, "status", "error",
              "error", e.getMessage() == null ? "unknown" : e.getMessage()));
        }
      }
    }
    return java.util.Map.of("results", results);
  }

  private DocumentDto handleUpload(
      String kbId,
      MultipartFile file,
      String category,
      String tags
  ) throws IOException {
    if (file.isEmpty()) throw new IllegalArgumentException("empty file");

    String docId = IdUtil.newId("doc");
    String filename = sanitize(file.getOriginalFilename());

    Path base = Path.of(cfg.dataDir()).toAbsolutePath().normalize();
    Path dir = base.resolve("uploads").resolve(kbId).resolve(docId);
    Files.createDirectories(dir);
    Path saved = dir.resolve(filename);
    try (java.io.InputStream in = file.getInputStream();
         java.io.OutputStream out = Files.newOutputStream(saved)) {
      in.transferTo(out);
    }

    byte[] bytes = Files.readAllBytes(saved);
    String sha = HashUtil.sha256Hex(bytes);
    Instant now = Instant.now();

    String contentType = file.getContentType() == null ? "" : file.getContentType();
    String cat = (category == null) ? "" : category.trim();
    List<String> tagList = parseTags(tags);

    DocumentDto doc = new DocumentDto(
        docId,
        filename,
        contentType,
        Files.size(saved),
        sha,
        now.toString(),
        now.toString(),
        cat,
        tagList
    );

    ArrayList<ChunkDto> chunks = parseAndChunkOrThrow(saved, contentType, docId, filename);
    index.upsert(kbId, new UpsertRequest(doc, chunks));

    return doc;
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
      String hint = filename + "#chunk=" + i;
      chunks.add(new ChunkDto(chunkId, docId, i, pieces.get(i), hint));
    }
    return chunks;
  }

  private static List<String> parseTags(String tags) {
    if (tags == null) return List.of();
    String t = tags.trim();
    if (t.isEmpty()) return List.of();

    String[] parts = t.split("[,，;；\\n\\r\\t ]+");
    ArrayList<String> out = new ArrayList<>();
    for (String p : parts) {
      String s = p.trim();
      if (s.isEmpty()) continue;
      if (s.length() > MAX_TAG_LENGTH) s = s.substring(0, MAX_TAG_LENGTH);
      if (!out.contains(s)) out.add(s);
      if (out.size() >= MAX_TAGS_COUNT) break;
    }
    return out;
  }

  private static String sanitize(String filename) {
    if (filename == null || filename.isBlank()) return "upload.bin";
    String s = filename.replace('\\', '_').replace('/', '_');
    s = s.replaceAll("[\\r\\n\\t]", "_");
    s = s.replaceAll("[:*?\"<>|]", "_");
    if (s.length() > MAX_FILENAME_LENGTH) s = s.substring(s.length() - MAX_FILENAME_LENGTH);
    return s;
  }

}
