package com.codec.kb.gateway.tenant;

import com.codec.kb.common.CreateKnowledgeBaseRequest;
import com.codec.kb.common.KnowledgeBaseDto;
import com.codec.kb.common.util.WebUtils;
import com.codec.kb.gateway.auth.AppUserPrincipal;
import com.codec.kb.gateway.auth.AuthUtil;
import com.codec.kb.gateway.auth.UserAuthContract;
import com.codec.kb.gateway.clients.InternalDocClient;
import com.codec.kb.gateway.clients.InternalIndexClient;
import com.codec.kb.gateway.kb.UserKbProvisioningService;
import com.codec.kb.gateway.store.AppUserEntity;
import com.codec.kb.gateway.store.AppUserRepository;
import com.codec.kb.gateway.store.KbSettingsEntity;
import com.codec.kb.gateway.store.KbSettingsRepository;
import com.codec.kb.gateway.store.UserStatus;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant")
public class TenantAdminController {
  private final AppUserRepository users;
  private final KbSettingsRepository kbSettings;
  private final UserKbProvisioningService provisioning;
  private final InternalIndexClient index;
  private final InternalDocClient docs;

  public TenantAdminController(
      AppUserRepository users,
      KbSettingsRepository kbSettings,
      UserKbProvisioningService provisioning,
      InternalIndexClient index,
      InternalDocClient docs) {
    this.users = users;
    this.kbSettings = kbSettings;
    this.provisioning = provisioning;
    this.index = index;
    this.docs = docs;
  }

  @GetMapping("/users")
  public Map<String, Object> listTenantUsers() {
    AppUserPrincipal p = AuthUtil.requirePrincipal();
    UUID tenantId = p.tenantId();
    if (tenantId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no tenant");
    List<AppUserEntity> all = users.findByTenantId(tenantId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (AppUserEntity u : all) {
      if (u == null) continue;
      out.add(UserAuthContract.toUserRow(u));
    }
    return Map.of("users", out);
  }

  @PostMapping("/users/{id}/approve")
  public Map<String, Object> approveUser(@PathVariable("id") String id) {
    AppUserPrincipal p = AuthUtil.requirePrincipal();
    UUID tenantId = p.tenantId();
    UUID uid = parseUuid(id);
    AppUserEntity u = requireSameTenant(uid, tenantId);
    u.setStatus(UserStatus.ACTIVE);
    users.save(u);
    provisioning.ensureDefaultKb(u.getId());
    return Map.of("ok", true);
  }

  @PostMapping("/users/{id}/reject")
  public Map<String, Object> rejectUser(@PathVariable("id") String id) {
    AppUserPrincipal p = AuthUtil.requirePrincipal();
    UUID tenantId = p.tenantId();
    UUID uid = parseUuid(id);
    AppUserEntity u = requireSameTenant(uid, tenantId);
    u.setStatus(UserStatus.REJECTED);
    users.save(u);
    return Map.of("ok", true);
  }

  @GetMapping("/kbs")
  public Map<String, Object> listTenantKbs() {
    UUID tenantId = requireTenantId();
    List<KbSettingsEntity> kbs = kbSettings.findByTenantIdAndPublicAccessFalse(tenantId);
    LinkedHashMap<String, KnowledgeBaseDto> knownKbs = new LinkedHashMap<>();
    for (KnowledgeBaseDto kb : index.listKbs()) {
      if (kb == null || kb.id() == null || kb.id().isBlank()) continue;
      knownKbs.put(kb.id(), kb);
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (KbSettingsEntity k : kbs) {
      if (k == null) continue;
      KnowledgeBaseDto kb = knownKbs.get(k.getKbId());
      if (kb == null) continue;
      out.add(toKbRow(kb, k));
    }
    return Map.of("kbs", out);
  }

  @PostMapping(path = "/kbs", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> createTenantKb(@RequestBody CreateKnowledgeBaseRequest req) {
    UUID tenantId = requireTenantId();
    KnowledgeBaseDto kb = index.createKb(req);
    if (kb == null || kb.id() == null || kb.id().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "create KB failed");
    }

    KbSettingsEntity settings = new KbSettingsEntity();
    settings.setKbId(kb.id());
    settings.setDocumentCount(UserKbProvisioningService.DEFAULT_DOCUMENT_COUNT);
    settings.setPublicAccess(false);
    settings.setTenantId(tenantId);
    kbSettings.save(settings);
    return toKbRow(kb, settings);
  }

  @DeleteMapping("/kbs/{id}")
  public Map<String, Object> deleteTenantKb(@PathVariable("id") String id) {
    String kbId = requireTenantSharedKb(id, requireTenantId()).getKbId();
    return deleteKb(kbId);
  }

  @GetMapping("/kbs/{kbId}/stats")
  public Map<String, Object> tenantKbStats(@PathVariable("kbId") String kbId) {
    String id = requireTenantSharedKb(kbId, requireTenantId()).getKbId();
    return index.stats(id);
  }

  @GetMapping("/kbs/{kbId}/documents")
  public Map<String, Object> tenantKbDocuments(@PathVariable("kbId") String kbId) {
    String id = requireTenantSharedKb(kbId, requireTenantId()).getKbId();
    return index.listDocuments(id);
  }

  @PostMapping(path = "/kbs/{kbId}/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object uploadTenantKbDocument(
      @PathVariable("kbId") String kbId,
      @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
      @RequestPart(value = "category", required = false) String category,
      @RequestPart(value = "tags", required = false) String tags) throws java.io.IOException {
    String id = requireTenantSharedKb(kbId, requireTenantId()).getKbId();
    if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    return docs.uploadToKb(id, file, category, tags);
  }

  @DeleteMapping("/kbs/{kbId}/documents/{id}")
  public Map<String, Object> deleteTenantKbDocument(
      @PathVariable("kbId") String kbId,
      @PathVariable("id") String documentId) {
    String id = requireTenantSharedKb(kbId, requireTenantId()).getKbId();
    String docId = String.valueOf(documentId == null ? "" : documentId).trim();
    if (docId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "docId is required");
    return index.deleteDocument(id, docId);
  }

  @PostMapping("/kbs/{kbId}/reindex")
  public Map<String, Object> reindexTenantKb(@PathVariable("kbId") String kbId) {
    String id = requireTenantSharedKb(kbId, requireTenantId()).getKbId();
    return index.reindexKb(id);
  }

  private AppUserEntity requireSameTenant(UUID userId, UUID tenantId) {
    AppUserEntity u = users.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
    if (tenantId != null && !tenantId.equals(u.getTenantId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }
    return u;
  }

  private static UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
    }
  }

  private UUID requireTenantId() {
    AppUserPrincipal p = AuthUtil.requirePrincipal();
    UUID tenantId = p.tenantId();
    if (tenantId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no tenant");
    return tenantId;
  }

  private KbSettingsEntity requireTenantSharedKb(String kbId, UUID tenantId) {
    String id = WebUtils.requireKbId(kbId);
    return kbSettings.findById(id)
        .filter(settings -> !settings.isPublicAccess())
        .filter(settings -> tenantId.equals(settings.getTenantId()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
  }

  private Map<String, Object> deleteKb(String kbId) {
    boolean ok = index.deleteKb(kbId);
    kbSettings.deleteById(kbId);
    return Map.of("ok", ok);
  }

  private static Map<String, Object> toKbRow(KnowledgeBaseDto kb, KbSettingsEntity settings) {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("id", kb.id());
    row.put("name", kb.name());
    row.put("embeddingMode", kb.embeddingMode());
    row.put("embeddingModel", kb.embeddingModel());
    row.put("embeddingBaseUrl", kb.embeddingBaseUrl());
    row.put("createdAt", kb.createdAt());
    row.put("updatedAt", kb.updatedAt());
    row.put("documentCount", settings.getDocumentCount());
    row.put("isPublic", false);
    row.put("isSystem", false);
    return row;
  }
}
