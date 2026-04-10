package com.codec.kb.gateway.kb;

import com.codec.kb.common.KnowledgeBaseDto;
import com.codec.kb.common.CreateKnowledgeBaseRequest;
import com.codec.kb.common.util.WebUtils;
import static com.codec.kb.common.util.WebUtils.safeTrim;
import com.codec.kb.gateway.GatewayConfig;
import com.codec.kb.gateway.auth.AuthUtil;
import com.codec.kb.gateway.clients.InternalDocClient;
import com.codec.kb.gateway.clients.InternalIndexClient;
import com.codec.kb.gateway.store.KbSettingsEntity;
import com.codec.kb.gateway.store.KbSettingsRepository;
import com.codec.kb.gateway.store.UserKbEntity;
import com.codec.kb.gateway.store.UserKbId;
import com.codec.kb.gateway.store.UserKbRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/kbs")
public class UserKbController {
  private static final String INTERNAL_HEADER = "X-Internal-Token";

  private final GatewayConfig cfg;
  private final RestClient rc;
  private final InternalIndexClient index;
  private final InternalDocClient docs;
  private final UserKbRepository userKbs;
  private final KbSettingsRepository kbSettings;
  private final UserKbProvisioningService provisioning;

  public UserKbController(
      GatewayConfig cfg,
      RestClient rc,
      InternalIndexClient index,
      InternalDocClient docs,
      UserKbRepository userKbs,
      KbSettingsRepository kbSettings,
      UserKbProvisioningService provisioning
  ) {
    this.cfg = cfg;
    this.rc = rc;
    this.index = index;
    this.docs = docs;
    this.userKbs = userKbs;
    this.kbSettings = kbSettings;
    this.provisioning = provisioning;
  }

  @GetMapping
  public Map<String, Object> listMine() {
    UUID uid = AuthUtil.requirePrincipal().id();

    provisioning.ensureDefaultKb(uid);
    Set<String> publicKbIds = new LinkedHashSet<>(provisioning.listPublicKbIds());
    List<UserKbEntity> links = userKbs.findByIdUserId(uid);

    Set<String> mine = new LinkedHashSet<>();
    Set<String> defaults = new HashSet<>();
    for (UserKbEntity l : links) {
      if (l == null || l.getKbId() == null) continue;
      mine.add(l.getKbId());
      if (l.isDefault()) defaults.add(l.getKbId());
    }

    List<KnowledgeBaseDto> all = index.listKbs();
    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (KnowledgeBaseDto kb : all) {
      if (kb == null || kb.id() == null) continue;
      boolean owned = mine.contains(kb.id());
      boolean isPublic = publicKbIds.contains(kb.id());
      if (!owned && !isPublic) continue;
      int docCount = kbSettings.findById(kb.id()).map(KbSettingsEntity::getDocumentCount)
          .orElse(UserKbProvisioningService.DEFAULT_DOCUMENT_COUNT);
      Map<String, Object> stats = index.stats(kb.id());
      long actualDocumentCount = asLong(stats == null ? null : stats.get("documents"));
      long sizeBytes = asLong(stats == null ? null : stats.get("sizeBytes"));
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", kb.id());
      row.put("name", kb.name());
      row.put("embeddingMode", kb.embeddingMode());
      row.put("embeddingModel", kb.embeddingModel());
      row.put("embeddingBaseUrl", kb.embeddingBaseUrl());
      row.put("createdAt", kb.createdAt());
      row.put("updatedAt", kb.updatedAt());
      row.put("isDefault", defaults.contains(kb.id()));
      row.put("isPublic", isPublic);
      row.put("owned", owned);
      row.put("isSystem", UserKbProvisioningService.isSharedCompanyKb(kb.id()));
      row.put("documentCount", docCount);
      row.put("actualDocumentCount", actualDocumentCount);
      row.put("sizeBytes", sizeBytes);
      out.add(row);
    }
    return Map.of("kbs", out);
  }

  public record CreateKbRequest(
      @NotBlank String name,
      String embeddingMode,
      String embeddingModel,
      String embeddingBaseUrl,
      Integer documentCount
  ) {}

  public record UpdateKbRequest(
      @NotBlank String name,
      Integer documentCount
  ) {}

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> create(@RequestBody CreateKbRequest req) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String name = req == null ? "" : safeTrim(req.name());
    if (name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    if (name.length() > 64) name = name.substring(0, 64);

    String mode = req == null ? "" : safeTrim(req.embeddingMode());
    if (mode.isBlank()) mode = "local";
    mode = mode.toLowerCase();
    if (!mode.equals("local") && !mode.equals("api")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embeddingMode must be local|api");
    }

    String model = req == null ? "" : safeTrim(req.embeddingModel());
    String baseUrl = req == null ? "" : safeTrim(req.embeddingBaseUrl());
    if (!baseUrl.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "embeddingBaseUrl is not allowed for user-created KBs; use a managed model UUID instead");
    }
    Integer dc = req == null ? null : req.documentCount();
    int documentCount = (dc == null) ? UserKbProvisioningService.DEFAULT_DOCUMENT_COUNT : WebUtils.clamp(dc, 1, 50);

    KnowledgeBaseDto kb = index.createKb(new CreateKnowledgeBaseRequest(
        name,
        mode,
        model.isBlank() ? null : model,
        baseUrl.isBlank() ? null : baseUrl
    ));
    if (kb == null || kb.id() == null || kb.id().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "create KB failed");
    }

    UserKbEntity link = new UserKbEntity();
    link.setId(new UserKbId(uid, kb.id()));
    link.setDefault(false);
    userKbs.save(link);

    KbSettingsEntity s = new KbSettingsEntity();
    s.setKbId(kb.id());
    s.setDocumentCount(documentCount);
    s.setPublicAccess(false);
    kbSettings.save(s);

    return toKbRow(kb, false, false, true, documentCount, 0L, 0L);
  }

  @PatchMapping(path = "/{kbId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> update(@PathVariable("kbId") String kbId, @RequestBody UpdateKbRequest req) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);

    KnowledgeBaseDto current = findKbOrThrow(id);
    String name = req == null ? "" : safeTrim(req.name());
    if (name.isBlank()) name = safeTrim(current.name());
    if (name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    if (name.length() > 64) name = name.substring(0, 64);

    Integer dc = req == null ? null : req.documentCount();
    int currentDocumentCount = kbSettings.findById(id).map(KbSettingsEntity::getDocumentCount)
        .orElse(UserKbProvisioningService.DEFAULT_DOCUMENT_COUNT);
    int documentCount = dc == null ? currentDocumentCount : WebUtils.clamp(dc, 1, 50);

    KnowledgeBaseDto kb = index.updateKb(id, new CreateKnowledgeBaseRequest(
        name,
        current.embeddingMode(),
        current.embeddingModel(),
        current.embeddingBaseUrl()
    ));

    KbSettingsEntity s = kbSettings.findById(id).orElseGet(KbSettingsEntity::new);
    s.setKbId(id);
    s.setDocumentCount(documentCount);
    s.setPublicAccess(false);
    kbSettings.save(s);

    boolean isDefault = userKbs.findById(new UserKbId(uid, id)).map(UserKbEntity::isDefault).orElse(false);
    return toKbRow(kb, isDefault, false, true, documentCount, 0L, 0L);
  }

  private Map<String, Object> toKbRow(
      KnowledgeBaseDto kb,
      boolean isDefault,
      boolean isPublic,
      boolean owned,
      int documentCount,
      long actualDocumentCount,
      long sizeBytes
  ) {
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    out.put("id", kb.id());
    out.put("name", kb.name());
    out.put("embeddingMode", kb.embeddingMode());
    out.put("embeddingModel", kb.embeddingModel());
    out.put("embeddingBaseUrl", kb.embeddingBaseUrl());
    out.put("createdAt", kb.createdAt());
    out.put("updatedAt", kb.updatedAt());
    out.put("isDefault", isDefault);
    out.put("isPublic", isPublic);
    out.put("owned", owned);
    out.put("isSystem", UserKbProvisioningService.isSharedCompanyKb(kb.id()));
    out.put("documentCount", documentCount);
    out.put("actualDocumentCount", actualDocumentCount);
    out.put("sizeBytes", sizeBytes);
    return out;
  }

  private KnowledgeBaseDto findKbOrThrow(String kbId) {
    for (KnowledgeBaseDto kb : index.listKbs()) {
      if (kb != null && kb.id() != null && kb.id().equals(kbId)) return kb;
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
  }

  @DeleteMapping("/{kbId}")
  public Map<String, Object> delete(@PathVariable("kbId") String kbId) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireKbId(kbId);

    if (UserKbProvisioningService.isSharedCompanyKb(id)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, UserKbProvisioningService.SHARED_COMPANY_KB_NAME + "不可删除");
    }

    UserKbEntity link = userKbs.findById(new UserKbId(uid, id)).orElse(null);
    if (link == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");

    Set<String> publicKbIds = new LinkedHashSet<>(provisioning.listPublicKbIds());
    ArrayList<UserKbEntity> remainingLinks = new ArrayList<>();
    for (UserKbEntity item : userKbs.findByIdUserId(uid)) {
      if (item == null || item.getKbId() == null || item.getKbId().isBlank()) continue;
      if (id.equals(item.getKbId())) continue;
      remainingLinks.add(item);
    }
    boolean hasOtherAccessibleKb = !remainingLinks.isEmpty() || !publicKbIds.isEmpty();
    if (!hasOtherAccessibleKb) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少保留一个知识库");
    }

    boolean ok = index.deleteKb(id);
    userKbs.delete(link);
    kbSettings.deleteById(id);

    if (link.isDefault() && !remainingLinks.isEmpty()) {
      UserKbEntity next = remainingLinks.get(0);
      next.setDefault(true);
      userKbs.save(next);
    }
    return Map.of("ok", ok);
  }

  @GetMapping("/{kbId}/documents")
  public Object listDocuments(@PathVariable("kbId") String kbId) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);
    return index.listDocuments(id);
  }

  @DeleteMapping("/{kbId}/documents/{docId}")
  public Object deleteDocument(@PathVariable("kbId") String kbId, @PathVariable("docId") String docId) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);
    String doc = safeTrim(docId);
    if (doc.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "docId is required");
    return index.deleteDocument(id, doc);
  }

  @GetMapping("/{kbId}/chunks/{chunkId}")
  public Object getChunk(@PathVariable("kbId") String kbId, @PathVariable("chunkId") String chunkId) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireReadableKb(uid, kbId);
    String chunk = safeTrim(chunkId);
    if (chunk.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkId is required");
    return index.getChunk(id, chunk);
  }

  @PostMapping(path = "/{kbId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object upload(
      @PathVariable("kbId") String kbId,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "category", required = false) String category,
      @RequestPart(value = "tags", required = false) String tags
  ) throws IOException {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);
    if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    return docs.uploadToKb(id, file, category, tags);
  }

  private String requireOwnedKb(UUID uid, String kbId) {
    String id = requireKbId(kbId);
    if (UserKbProvisioningService.isSharedCompanyKb(id)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该知识库仅管理员可维护");
    }
    if (!userKbs.existsByIdUserIdAndIdKbId(uid, id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
    }
    return id;
  }

  private String requireReadableKb(UUID uid, String kbId) {
    String id = requireKbId(kbId);
    if (userKbs.existsByIdUserIdAndIdKbId(uid, id)) return id;
    if (provisioning.listAccessibleKbIds(uid).contains(id)) return id;
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
  }

  private static String requireKbId(String kbId) {
    return WebUtils.requireKbId(kbId);
  }

  @PostMapping(path = "/{kbId}/documents/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object userBatchUpload(
      @PathVariable("kbId") String kbId,
      @RequestPart("files") List<MultipartFile> files
  ) throws IOException {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);
    ArrayList<Object> results = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) continue;
      try {
        Object result = docs.uploadToKb(id, file, null, null);
        results.add(Map.of(
            "filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
            "status", "ok",
            "result", result != null ? result : Map.of()
        ));
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

  @PostMapping(path = "/{kbId}/uploads")
  public Object initResumableUpload(
      @PathVariable("kbId") String kbId,
      @RequestBody(required = false) Map<String, Object> body
  ) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);
    return rc.post()
        .uri(cfg.docUrl() + "/internal/kbs/" + id + "/uploads")
        .header(INTERNAL_HEADER, cfg.internalToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body != null ? body : Map.of())
        .retrieve()
        .body(Object.class);
  }

  @org.springframework.web.bind.annotation.PatchMapping(path = "/{kbId}/uploads/{uploadId}")
  public Object uploadChunk(
      @PathVariable("kbId") String kbId,
      @PathVariable("uploadId") String uploadId,
      @RequestBody byte[] chunkData,
      HttpServletRequest request
  ) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);
    String contentRange = request.getHeader("Content-Range");
    var req2 = rc.method(HttpMethod.PATCH)
        .uri(cfg.docUrl() + "/internal/kbs/" + id + "/uploads/" + uploadId)
        .header(INTERNAL_HEADER, cfg.internalToken())
        .contentType(MediaType.APPLICATION_OCTET_STREAM);
    if (contentRange != null) req2 = req2.header("Content-Range", contentRange);
    return req2.body(chunkData).retrieve().body(Object.class);
  }

  @GetMapping(path = "/{kbId}/uploads/{uploadId}")
  public Object getUploadStatus(
      @PathVariable("kbId") String kbId,
      @PathVariable("uploadId") String uploadId
  ) {
    UUID uid = AuthUtil.requirePrincipal().id();
    String id = requireOwnedKb(uid, kbId);
    return rc.get()
        .uri(cfg.docUrl() + "/internal/kbs/" + id + "/uploads/" + uploadId)
        .header(INTERNAL_HEADER, cfg.internalToken())
        .retrieve()
        .body(Object.class);
  }


  private static long asLong(Object value) {
    if (value instanceof Number number) return number.longValue();
    if (value == null) return 0L;
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException ignored) {
      return 0L;
    }
  }
}
