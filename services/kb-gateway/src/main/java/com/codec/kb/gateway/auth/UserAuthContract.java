package com.codec.kb.gateway.auth;

import com.codec.kb.gateway.store.AppUserEntity;
import com.codec.kb.gateway.store.UserRole;

import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UserAuthContract {
  private UserAuthContract() {}

  public static UserRole normalizedBaseRole(UserRole role) {
    return role == UserRole.ADMIN ? UserRole.ADMIN : UserRole.USER;
  }

  public static boolean isTenantAdmin(UserRole role, boolean isTenantAdmin) {
    return role == UserRole.TENANT_ADMIN || isTenantAdmin;
  }

  public static UserRole effectiveRole(UserRole role, boolean isTenantAdmin) {
    UserRole baseRole = normalizedBaseRole(role);
    if (baseRole == UserRole.ADMIN) {
      return UserRole.ADMIN;
    }
    return isTenantAdmin(role, isTenantAdmin) ? UserRole.TENANT_ADMIN : UserRole.USER;
  }

  public static void normalizeLegacyTenantAdmin(AppUserEntity user) {
    if (user != null && user.getRole() == UserRole.TENANT_ADMIN) {
      user.setRole(UserRole.USER);
      user.setTenantAdmin(true);
    }
  }

  public static AppUserPrincipal toPrincipal(AppUserEntity user) {
    return new AppUserPrincipal(
        user.getId(),
        nullToEmpty(user.getUsername()),
        nullToEmpty(user.getPasswordHash()),
        nullToEmpty(user.getDisplayName()),
        user.getRole(),
        user.getStatus(),
        user.getTenantId(),
        user.isTenantAdmin());
  }

  public static Map<String, Object> toUserRow(AppUserEntity user) {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("id", String.valueOf(user.getId()));
    row.put("username", nullToEmpty(user.getUsername()));
    row.put("displayName", nullToEmpty(user.getDisplayName()));
    row.put("role", normalizedBaseRole(user.getRole()).name());
    row.put("effectiveRole", effectiveRole(user.getRole(), user.isTenantAdmin()).name());
    row.put("isTenantAdmin", isTenantAdmin(user.getRole(), user.isTenantAdmin()));
    row.put("tenantId", user.getTenantId() == null ? "" : user.getTenantId().toString());
    row.put("status", user.getStatus() == null ? "" : user.getStatus().name());
    row.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
    return row;
  }

  public static Map<String, Object> toMePayload(AppUserEntity user, AppUserPrincipal principal) {
    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", String.valueOf(user.getId()));
    payload.put("username", nullToEmpty(user.getUsername()));
    payload.put("displayName", nullToEmpty(user.getDisplayName()));
    payload.put("avatarDataUrl", nullToEmpty(user.getAvatarDataUrl()));
    payload.put("role", normalizedBaseRole(user.getRole()).name());
    payload.put("effectiveRole", effectiveRole(user.getRole(), user.isTenantAdmin()).name());
    payload.put("isTenantAdmin", isTenantAdmin(user.getRole(), user.isTenantAdmin()));
    payload.put("tenantId", user.getTenantId() == null ? "" : user.getTenantId().toString());
    payload.put("authorities", authorityNames(principal.getAuthorities()));
    payload.put("status", user.getStatus() == null ? "" : user.getStatus().name());
    return payload;
  }

  public static List<String> authorityNames(Iterable<? extends GrantedAuthority> authorities) {
    ArrayList<String> names = new ArrayList<>();
    for (GrantedAuthority authority : authorities) {
      if (authority == null) {
        continue;
      }
      String name = authority.getAuthority();
      if (name != null && !names.contains(name)) {
        names.add(name);
      }
    }
    return List.copyOf(names);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
