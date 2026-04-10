package com.codec.kb.gateway.auth;

import com.codec.kb.gateway.store.UserRole;
import com.codec.kb.gateway.store.UserStatus;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class AppUserPrincipal implements UserDetails {
  private final UUID id;
  private final String username;
  private final String passwordHash;
  private final String displayName;
  private final UserRole role;
  private final UserStatus status;
  private final UUID tenantId;
  private final boolean isTenantAdmin;

  public AppUserPrincipal(
      UUID id,
      String username,
      String passwordHash,
      String displayName,
      UserRole role,
      UserStatus status,
      UUID tenantId,
      boolean isTenantAdmin
  ) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.role = UserAuthContract.normalizedBaseRole(role);
    this.status = status;
    this.tenantId = tenantId;
    this.isTenantAdmin = UserAuthContract.isTenantAdmin(role, isTenantAdmin);
  }

  public UUID id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public UserRole role() {
    return role;
  }

  public UserStatus status() {
    return status;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public boolean isTenantAdmin() {
    return isTenantAdmin;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    String r = role == null ? "USER" : role.name();
    List<GrantedAuthority> auths = new java.util.ArrayList<>();
    auths.add(new SimpleGrantedAuthority("ROLE_" + r));
    if (isTenantAdmin && role != UserRole.ADMIN) {
      auths.add(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
    }
    return auths;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return status != UserStatus.DISABLED;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return status == UserStatus.ACTIVE;
  }
}
