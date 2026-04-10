package com.codec.kb.gateway.admin;

import com.codec.kb.gateway.store.TenantEntity;
import com.codec.kb.gateway.store.TenantRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tenants")
public class AdminTenantController {
  private final TenantRepository repo;
  private static final SecureRandom RNG = new SecureRandom();

  public AdminTenantController(TenantRepository repo) {
    this.repo = repo;
  }

  public record CreateTenantRequest(String name, String slug) {}
  public record UpdateTenantRequest(String name, Boolean enabled) {}

  @GetMapping
  public Map<String, Object> list() {
    List<TenantEntity> tenants = repo.findAll();
    List<Map<String, Object>> out = new ArrayList<>();
    for (TenantEntity t : tenants) {
      out.add(toMap(t));
    }
    return Map.of("tenants", out);
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody CreateTenantRequest req) {
    String name = (req == null || req.name() == null) ? "" : req.name().trim();
    String slug = (req == null || req.slug() == null) ? "" : req.slug().trim().toLowerCase();
    if (name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
    if (slug.isBlank() || !slug.matches("[a-z0-9][a-z0-9-]{0,62}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug格式不正确");
    }
    if (repo.findBySlug(slug).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "slug已存在");
    }

    TenantEntity t = new TenantEntity();
    t.setName(name);
    t.setSlug(slug);
    t.setInviteCode(generateCode());
    t.setEnabled(true);
    repo.save(t);
    return toMap(t);
  }

  @PatchMapping("/{id}")
  public Map<String, Object> update(@PathVariable("id") String id, @RequestBody UpdateTenantRequest req) {
    UUID tid = parseUuid(id);
    TenantEntity t = repo.findById(tid)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
    if (req.name() != null && !req.name().isBlank()) t.setName(req.name().trim());
    if (req.enabled() != null) t.setEnabled(req.enabled());
    repo.save(t);
    return toMap(t);
  }

  @PostMapping("/{id}/rotate-code")
  public Map<String, Object> rotateCode(@PathVariable("id") String id) {
    UUID tid = parseUuid(id);
    TenantEntity t = repo.findById(tid)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
    String newCode = generateCode();
    t.setInviteCode(newCode);
    repo.save(t);
    return Map.of("ok", true, "inviteCode", newCode);
  }

  private static Map<String, Object> toMap(TenantEntity t) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    map.put("id", t.getId().toString());
    map.put("name", t.getName());
    map.put("slug", t.getSlug());
    map.put("inviteCode", t.getInviteCode());
    map.put("enabled", t.isEnabled());
    map.put("createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().toString());
    return map;
  }

  private String generateCode() {
    byte[] bytes = new byte[12];
    RNG.nextBytes(bytes);
    String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    if (repo.existsByInviteCode(code)) return generateCode();
    return code;
  }

  private static UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
    }
  }
}
