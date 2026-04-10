package com.codec.kb.gateway.admin;

import com.codec.kb.common.CreateKnowledgeBaseRequest;
import com.codec.kb.common.util.WebUtils;
import com.codec.kb.gateway.GatewayConfig;
import com.codec.kb.gateway.kb.UserKbProvisioningService;
import com.codec.kb.gateway.store.KbSettingsEntity;
import com.codec.kb.gateway.store.KbSettingsRepository;
import com.codec.kb.gateway.store.UserKbEntity;
import com.codec.kb.gateway.store.UserKbRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
public class AdminDocumentController {
  private static final String INTERNAL_HEADER = "X-Internal-Token";
  private static final String DEFAULT_KB_ID = "default";

  private final GatewayConfig cfg;
  private final RestClient rc;
  private final ObjectMapper om;
  private final UserKbRepository userKbs;
  private final KbSettingsRepository kbSettings;
  private final UserKbProvisioningService provisioning;

  public AdminDocumentController(
      GatewayConfig cfg,
      RestClient rc,
      ObjectMapper om,
      UserKbRepository userKbs,
      KbSettingsRepository kbSettings,
      UserKbProvisioningService provisioning
  ) {
    this.cfg = cfg;
    this.rc = rc;
    this.om = om;
    this.userKbs = userKbs;
    this.kbSettings = kbSettings;
    this.provisioning = provisioning;
  }

  @PostMapping(path = "/api/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object adminUpload(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "category", required = false) String category,
      @RequestPart(value = "tags", required = false) String tags
  ) throws IOException {
    return adminUploadToKb(DEFAULT_KB_ID, file, category, tags);
  }

  @PostMapping(path = "/api/admin/kbs/{kbId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object adminUploadToKb(
      @PathVariable("kbId") String kbId,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "category", required = false) String category,
      @RequestPart(value = "tags", required = false) String tags
  ) throws IOException {
    String adminKbId = requireAdminReadableKb(kbId);
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart(file));
    if (category != null) body.add("category", category);
    if (tags != null) body.add("tags", tags);

    return rc.post()
        .uri(cfg.docUrl() + "/internal/kbs/" + adminKbId + "/documents/upload")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/api/admin/documents")
  public Object adminDocuments() {
    return adminDocuments(DEFAULT_KB_ID);
  }

  @GetMapping("/api/admin/kbs/{kbId}/documents")
  public Object adminDocuments(@PathVariable("kbId") String kbId) {
    String adminKbId = requireAdminReadableKb(kbId);
    return rc.get()
        .uri(cfg.indexUrl() + "/internal/kbs/" + adminKbId + "/documents")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Object.class);
  }

  @DeleteMapping("/api/admin/documents/{id}")
  public Object adminDeleteDoc(@PathVariable("id") String id) {
    return adminDeleteDoc(DEFAULT_KB_ID, id);
  }

  @DeleteMapping("/api/admin/kbs/{kbId}/documents/{id}")
  public Object adminDeleteDoc(@PathVariable("kbId") String kbId, @PathVariable("id") String id) {
    String adminKbId = requireAdminReadableKb(kbId);
    return rc.delete()
        .uri(cfg.indexUrl() + "/internal/kbs/" + adminKbId + "/documents/" + id)
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Object.class);
  }

  @PostMapping("/api/admin/reindex")
  public Object adminReindex() {
    return adminReindex(DEFAULT_KB_ID);
  }

  @PostMapping("/api/admin/kbs/{kbId}/reindex")
  public Object adminReindex(@PathVariable("kbId") String kbId) {
    String adminKbId = requireAdminReadableKb(kbId);
    return rc.post()
        .uri(cfg.indexUrl() + "/internal/kbs/" + adminKbId + "/reindex")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Object.class);
  }

  @GetMapping("/api/admin/stats")
  public Map<String, Object> adminStats() {
    return adminStats(DEFAULT_KB_ID);
  }

  @GetMapping("/api/admin/kbs/{kbId}/stats")
  public Map<String, Object> adminStats(@PathVariable("kbId") String kbId) {
    String adminKbId = requireAdminReadableKb(kbId);
    Map<String, Object> idx = (Map<String, Object>) rc.get()
        .uri(cfg.indexUrl() + "/internal/kbs/" + adminKbId + "/stats")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Map.class);

    Map<String, Object> ai = (Map<String, Object>) rc.get()
        .uri(cfg.aiUrl() + "/internal/stats")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Map.class);

    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.putAll(idx);
    out.putAll(ai);
    return out;
  }

  @GetMapping("/api/admin/kbs")
  public Object adminListKbs() {
    provisioning.ensureSharedKbSettings();
    Map<String, Object> remote = (Map<String, Object>) rc.get()
        .uri(cfg.indexUrl() + "/internal/kbs")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Map.class);

    List<?> source = remote == null ? List.of() : (List<?>) remote.getOrDefault("kbs", List.of());
    LinkedHashSet<String> linkedKbIds = collectLinkedKbIds();

    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (Object item : source) {
      if (!(item instanceof Map<?, ?> raw)) continue;
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      raw.forEach((k, v) -> row.put(String.valueOf(k), v));
      String kbId = row.get("id") == null ? "" : String.valueOf(row.get("id"));
      boolean isPublic = isAdminVisibleKb(kbId, linkedKbIds);
      if (!isPublic) continue;
      row.put("isPublic", isPublic);
      row.put("isSystem", DEFAULT_KB_ID.equals(kbId));
      out.add(row);
    }
    return Map.of("kbs", out);
  }

  @PostMapping(path = "/api/admin/kbs", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Object adminCreateKb(@RequestBody CreateKnowledgeBaseRequest req) {
    Map<String, Object> created = (Map<String, Object>) rc.post()
        .uri(cfg.indexUrl() + "/internal/kbs")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(req)
        .retrieve()
        .body(Map.class);

    String kbId = created == null || created.get("id") == null ? "" : String.valueOf(created.get("id"));
    if (!kbId.isBlank()) {
      KbSettingsEntity settings = kbSettings.findById(kbId).orElseGet(KbSettingsEntity::new);
      settings.setKbId(kbId);
      if (settings.getDocumentCount() <= 0) settings.setDocumentCount(UserKbProvisioningService.DEFAULT_DOCUMENT_COUNT);
      settings.setPublicAccess(true);
      settings.setTenantId(null);
      kbSettings.save(settings);
    }
    return created;
  }

  @DeleteMapping("/api/admin/kbs/{id}")
  public Object adminDeleteKb(@PathVariable("id") String id) {
    String kbId = requireAdminReadableKb(id);
    if (DEFAULT_KB_ID.equals(kbId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, UserKbProvisioningService.SHARED_COMPANY_KB_NAME + "不可删除");
    }
    Map<String, Object> current = (Map<String, Object>) adminListKbs();
    List<?> kbs = current == null ? List.of() : (List<?>) current.getOrDefault("kbs", List.of());
    if (kbs.size() <= 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少保留一个知识库");
    }

    Map<String, Object> out = (Map<String, Object>) rc.delete()
        .uri(cfg.indexUrl() + "/internal/kbs/" + kbId)
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Map.class);
    userKbs.deleteByIdKbId(kbId);
    kbSettings.deleteById(kbId);
    return out;
  }

  @PostMapping(path = "/api/admin/kbs/{kbId}/documents/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object adminBatchUpload(
      @PathVariable("kbId") String kbId,
      @RequestPart("files") List<MultipartFile> files
  ) throws IOException {
    String adminKbId = requireAdminReadableKb(kbId);
    ArrayList<Object> results = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) continue;
      try {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart(file));
        Object result = rc.post()
            .uri(cfg.docUrl() + "/internal/kbs/" + adminKbId + "/documents/upload")
            .header(INTERNAL_HEADER, cfg.internalToken())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .body(Object.class);
        results.add(Map.of("filename", file.getOriginalFilename(), "status", "ok", "result", result != null ? result : Map.of()));
      } catch (Exception e) {
        results.add(Map.of(
            "filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
            "status", "error",
            "error", e.getMessage() == null ? "unknown" : e.getMessage()
        ));
      }
    }
    return Map.of("results", results);
  }

  @PostMapping(path = "/api/admin/kbs/{kbId}/documents/import-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object adminImportZip(
      @PathVariable("kbId") String kbId,
      @RequestPart("file") MultipartFile file
  ) throws IOException {
    String adminKbId = requireAdminReadableKb(kbId);
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart(file));
    return rc.post()
        .uri(cfg.docUrl() + "/internal/kbs/" + adminKbId + "/documents/import-zip")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(body)
        .retrieve()
        .body(Object.class);
  }

  @PostMapping(path = "/api/admin/kbs/{kbId}/uploads")
  public Object adminInitResumableUpload(
      @PathVariable("kbId") String kbId,
      @RequestBody(required = false) Map<String, Object> body
  ) {
    String id = requireAdminReadableKb(kbId);
    return rc.post()
        .uri(cfg.docUrl() + "/internal/kbs/" + id + "/uploads")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body != null ? body : Map.of())
        .retrieve()
        .body(Object.class);
  }

  // ─── Private helpers ───────────────────────────────────────────────────────

  private String requireAdminReadableKb(String kbId) {
    String id = requireKbId(kbId);
    if (!isAdminVisibleKb(id, collectLinkedKbIds())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
    }
    return id;
  }

  private LinkedHashSet<String> collectLinkedKbIds() {
    LinkedHashSet<String> linkedKbIds = new LinkedHashSet<>();
    for (UserKbEntity link : userKbs.findAll()) {
      if (link == null || link.getKbId() == null || link.getKbId().isBlank()) continue;
      linkedKbIds.add(link.getKbId());
    }
    return linkedKbIds;
  }

  private boolean isAdminVisibleKb(String kbId, LinkedHashSet<String> linkedKbIds) {
    if (kbId == null || kbId.isBlank()) return false;
    return kbSettings.findById(kbId)
        .map(KbSettingsEntity::isPublicAccess)
        .orElse(!linkedKbIds.contains(kbId));
  }

  private static String requireKbId(String kbId) {
    return WebUtils.requireKbId(kbId);
  }

  private static HttpEntity<InputStreamResource> filePart(MultipartFile file) throws IOException {
    InputStreamResource resource = new InputStreamResource(file.getInputStream()) {
      @Override
      public String getFilename() {
        return file.getOriginalFilename();
      }

      @Override
      public long contentLength() {
        return file.getSize();
      }
    };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    return new HttpEntity<>(resource, headers);
  }
}
