package com.codec.kb.gateway.kb;

import com.codec.kb.common.KnowledgeBaseDto;
import com.codec.kb.common.CreateKnowledgeBaseRequest;
import com.codec.kb.gateway.clients.InternalIndexClient;
import com.codec.kb.gateway.store.AppUserEntity;
import com.codec.kb.gateway.store.AppUserRepository;
import com.codec.kb.gateway.store.KbSettingsEntity;
import com.codec.kb.gateway.store.KbSettingsRepository;
import com.codec.kb.gateway.store.UserKbEntity;
import com.codec.kb.gateway.store.UserKbId;
import com.codec.kb.gateway.store.UserKbRepository;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserKbProvisioningService {
  public static final int DEFAULT_DOCUMENT_COUNT = 6;
  public static final String SHARED_COMPANY_KB_ID = "default";
  public static final String SHARED_COMPANY_KB_NAME = "公司章程";

  private final InternalIndexClient index;
  private final UserKbRepository userKbs;
  private final KbSettingsRepository kbSettings;
  private final AppUserRepository users;

  public UserKbProvisioningService(
      InternalIndexClient index,
      UserKbRepository userKbs,
      KbSettingsRepository kbSettings,
      AppUserRepository users) {
    this.index = index;
    this.userKbs = userKbs;
    this.kbSettings = kbSettings;
    this.users = users;
  }

  public synchronized void ensureDefaultKb(UUID userId) {
    if (userId == null) return;
    try {
      List<KnowledgeBaseDto> allKbs = index.listKbs();
      ensureSharedKbSettings(allKbs);
      Set<String> existingKbIds = new HashSet<>();
      for (KnowledgeBaseDto kb : allKbs) {
        if (kb == null || kb.id() == null || kb.id().isBlank()) continue;
        existingKbIds.add(kb.id());
      }

      List<UserKbEntity> links = new ArrayList<>(userKbs.findByIdUserId(userId));
      ArrayList<UserKbEntity> validLinks = new ArrayList<>();
      UserKbEntity defaultLink = null;
      for (UserKbEntity link : links) {
        if (link == null || link.getKbId() == null || link.getKbId().isBlank()) continue;
        if (SHARED_COMPANY_KB_ID.equals(link.getKbId())) {
          userKbs.delete(link);
          continue;
        }
        if (!existingKbIds.contains(link.getKbId())) {
          userKbs.delete(link);
          kbSettings.deleteById(link.getKbId());
          continue;
        }
        validLinks.add(link);
        ensureKbSettings(link.getKbId());
        if (link.isDefault() && defaultLink == null) defaultLink = link;
      }

      if (defaultLink != null) return;

      if (!validLinks.isEmpty()) {
        UserKbEntity first = validLinks.get(0);
        first.setDefault(true);
        userKbs.save(first);
        return;
      }

      if (!listPublicKbIds(allKbs).isEmpty()) {
        return;
      }

      KnowledgeBaseDto kb = index.createKb(new CreateKnowledgeBaseRequest("我的知识库", "local", null, null));
      String kbId = kb == null ? "" : kb.id();
      if (kbId == null || kbId.isBlank()) return;

      UserKbEntity link = new UserKbEntity();
      link.setId(new UserKbId(userId, kbId));
      link.setDefault(true);
      userKbs.save(link);
      ensureKbSettings(kbId);
    } catch (Exception ignored) {
      // best-effort provisioning
    }
  }

  public synchronized List<String> listAccessibleKbIds(UUID userId) {
    if (userId == null) return List.of();
    ensureDefaultKb(userId);

    List<KnowledgeBaseDto> allKbs = index.listKbs();
    Set<String> existingKbIds = new HashSet<>();
    for (KnowledgeBaseDto kb : allKbs) {
      if (kb == null || kb.id() == null || kb.id().isBlank()) continue;
      existingKbIds.add(kb.id());
    }

    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (UserKbEntity link : userKbs.findByIdUserId(userId)) {
      if (link == null || link.getKbId() == null || link.getKbId().isBlank()) continue;
      if (existingKbIds.contains(link.getKbId())) out.add(link.getKbId());
    }
    out.addAll(listPublicKbIds(allKbs));
    out.addAll(listTenantSharedKbIds(resolveTenantId(userId), existingKbIds));
    return List.copyOf(out);
  }

  public synchronized List<String> listPublicKbIds() {
    List<KnowledgeBaseDto> allKbs = index.listKbs();
    ensureSharedKbSettings(allKbs);
    return listPublicKbIds(allKbs);
  }

  private List<String> listPublicKbIds(List<KnowledgeBaseDto> allKbs) {
    LinkedHashSet<String> linkedKbIds = new LinkedHashSet<>();
    for (UserKbEntity link : userKbs.findAll()) {
      if (link == null || link.getKbId() == null || link.getKbId().isBlank()) continue;
      linkedKbIds.add(link.getKbId());
    }

    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (allKbs != null) {
      for (KnowledgeBaseDto kb : allKbs) {
        if (kb == null || kb.id() == null || kb.id().isBlank()) continue;
        boolean isPublic = kbSettings.findById(kb.id())
            .map(KbSettingsEntity::isPublicAccess)
            .orElse(!linkedKbIds.contains(kb.id()));
        if (isPublic) out.add(kb.id());
      }
    }
    return List.copyOf(out);
  }

  public synchronized void ensureSharedKbSettings() {
    ensureSharedKbSettings(index.listKbs());
  }

  public static boolean isSharedCompanyKb(String kbId) {
    return SHARED_COMPANY_KB_ID.equals(kbId);
  }

  private void ensureSharedKbSettings(List<KnowledgeBaseDto> allKbs) {
    if (allKbs == null) return;
    for (KnowledgeBaseDto kb : allKbs) {
      if (kb == null || kb.id() == null || kb.id().isBlank()) continue;
      if (!SHARED_COMPANY_KB_ID.equals(kb.id())) continue;
      KbSettingsEntity s = kbSettings.findById(kb.id()).orElseGet(KbSettingsEntity::new);
      s.setKbId(kb.id());
      if (s.getDocumentCount() <= 0) s.setDocumentCount(DEFAULT_DOCUMENT_COUNT);
      s.setPublicAccess(true);
      s.setTenantId(null);
      kbSettings.save(s);
      return;
    }
  }

  private void ensureKbSettings(String kbId) {
    if (kbId == null || kbId.isBlank()) return;
    if (kbSettings.findById(kbId).isPresent()) return;

    KbSettingsEntity s = new KbSettingsEntity();
    s.setKbId(kbId);
    s.setDocumentCount(DEFAULT_DOCUMENT_COUNT);
    s.setPublicAccess(isSharedCompanyKb(kbId));
    s.setTenantId(null);
    kbSettings.save(s);
  }

  private UUID resolveTenantId(UUID userId) {
    if (userId == null) return null;
    return users.findById(userId)
        .map(AppUserEntity::getTenantId)
        .orElse(null);
  }

  private List<String> listTenantSharedKbIds(UUID tenantId, Set<String> existingKbIds) {
    if (tenantId == null) return List.of();

    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (KbSettingsEntity settings : kbSettings.findByTenantIdAndPublicAccessFalse(tenantId)) {
      if (settings == null || settings.getKbId() == null || settings.getKbId().isBlank()) continue;
      if (existingKbIds.contains(settings.getKbId())) out.add(settings.getKbId());
    }
    return List.copyOf(out);
  }
}
