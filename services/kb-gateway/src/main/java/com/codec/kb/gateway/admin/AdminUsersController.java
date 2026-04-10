package com.codec.kb.gateway.admin;

import com.codec.kb.gateway.auth.AuthUtil;
import com.codec.kb.gateway.auth.UserAuthContract;
import com.codec.kb.gateway.kb.UserKbProvisioningService;
import com.codec.kb.gateway.store.AppUserEntity;
import com.codec.kb.gateway.store.AppUserRepository;
import com.codec.kb.gateway.store.UserRole;
import com.codec.kb.gateway.store.UserStatus;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUsersController {
  private final AppUserRepository users;
  private final UserKbProvisioningService provisioning;

  public AdminUsersController(AppUserRepository users, UserKbProvisioningService provisioning) {
    this.users = users;
    this.provisioning = provisioning;
  }

  @GetMapping
  public Map<String, Object> list(@RequestParam(name = "status", required = false) String status) {
    UserStatus s = null;
    if (status != null && !status.isBlank()) {
      try {
        s = UserStatus.valueOf(status.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
      }
    }

    ArrayList<Map<String, Object>> out = new ArrayList<>();
    for (AppUserEntity u : users.findAll()) {
      if (u == null) continue;
      if (s != null && u.getStatus() != s) continue;
      out.add(UserAuthContract.toUserRow(u));
    }
    return Map.of("users", out);
  }

  @PostMapping("/{id}/approve")
  public Map<String, Object> approve(@PathVariable("id") String id) {
    UUID uid = parseUuid(id);
    AppUserEntity u = users.findById(uid).orElse(null);
    if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
    if (u.getStatus() == UserStatus.ACTIVE) return Map.of("ok", true);
    u.setStatus(UserStatus.ACTIVE);
    users.save(u);
    provisioning.ensureDefaultKb(u.getId());
    return Map.of("ok", true);
  }

  @PostMapping("/{id}/reject")
  public Map<String, Object> reject(@PathVariable("id") String id) {
    UUID uid = parseUuid(id);
    AppUserEntity u = users.findById(uid).orElse(null);
    if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
    if (u.getStatus() == UserStatus.REJECTED) return Map.of("ok", true);
    u.setStatus(UserStatus.REJECTED);
    users.save(u);
    return Map.of("ok", true);
  }

  @PostMapping("/{id}/disable")
  public Map<String, Object> disable(@PathVariable("id") String id) {
    UUID currentAdminId = AuthUtil.requirePrincipal().id();
    AppUserEntity user = requireUser(parseUuid(id));
    rejectSelfMutation(currentAdminId, user);
    rejectAdminTarget(user);
    if (user.getStatus() == UserStatus.DISABLED) {
      return Map.of("ok", true);
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only active users can be disabled");
    }
    user.setStatus(UserStatus.DISABLED);
    users.save(user);
    return Map.of("ok", true);
  }

  @PostMapping("/{id}/restore")
  public Map<String, Object> restore(@PathVariable("id") String id) {
    AppUserEntity user = requireUser(parseUuid(id));
    rejectAdminTarget(user);
    if (user.getStatus() != UserStatus.DISABLED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only disabled users can be restored");
    }
    user.setStatus(UserStatus.ACTIVE);
    users.save(user);
    return Map.of("ok", true);
  }

  @PostMapping("/{id}/tenant-admin")
  public Map<String, Object> grantTenantAdmin(@PathVariable("id") String id) {
    AppUserEntity user = requireUser(parseUuid(id));
    UserAuthContract.normalizeLegacyTenantAdmin(user);
    if (user.getTenantId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user has no tenant");
    }
    user.setTenantAdmin(true);
    users.save(user);
    return Map.of("ok", true, "user", UserAuthContract.toUserRow(user));
  }

  @DeleteMapping("/{id}/tenant-admin")
  public Map<String, Object> revokeTenantAdmin(@PathVariable("id") String id) {
    AppUserEntity user = requireUser(parseUuid(id));
    if (user.getRole() == com.codec.kb.gateway.store.UserRole.TENANT_ADMIN) {
      user.setRole(com.codec.kb.gateway.store.UserRole.USER);
    }
    user.setTenantAdmin(false);
    users.save(user);
    return Map.of("ok", true, "user", UserAuthContract.toUserRow(user));
  }

  private AppUserEntity requireUser(UUID userId) {
    AppUserEntity user = users.findById(userId).orElse(null);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
    }
    return user;
  }

  private static void rejectSelfMutation(UUID currentAdminId, AppUserEntity user) {
    if (user != null && currentAdminId != null && currentAdminId.equals(user.getId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "current admin cannot disable self");
    }
  }

  private static void rejectAdminTarget(AppUserEntity user) {
    if (user != null && user.getRole() == UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "admin user status cannot be changed");
    }
  }

  private static UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
    }
  }
}
