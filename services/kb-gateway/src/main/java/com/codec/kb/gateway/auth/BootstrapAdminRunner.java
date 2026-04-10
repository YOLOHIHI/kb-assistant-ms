package com.codec.kb.gateway.auth;

import com.codec.kb.gateway.GatewayConfig;
import com.codec.kb.gateway.SecurityProps;
import com.codec.kb.gateway.store.AppUserEntity;
import com.codec.kb.gateway.store.AppUserRepository;
import com.codec.kb.gateway.store.TenantEntity;
import com.codec.kb.gateway.store.TenantRepository;
import com.codec.kb.gateway.store.UserRole;
import com.codec.kb.gateway.store.UserStatus;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
  private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String DEV_ADMIN_USER = "admin";
  private static final String DEV_ADMIN_PASSWORD = "admin123";

  private final AppUserRepository users;
  private final TenantRepository tenants;
  private final SecurityProps props;
  private final GatewayConfig gatewayConfig;
  private final PasswordEncoder encoder;

  public BootstrapAdminRunner(AppUserRepository users, TenantRepository tenants,
      SecurityProps props, GatewayConfig gatewayConfig, PasswordEncoder encoder) {
    this.users = users;
    this.tenants = tenants;
    this.props = props;
    this.gatewayConfig = gatewayConfig;
    this.encoder = encoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    ensureDefaultTenant();
    ensureAdminUser();
  }

  private void ensureDefaultTenant() {
    if (tenants.findById(DEFAULT_TENANT_ID).isPresent()) return;
    TenantEntity t = new TenantEntity();
    t.setId(DEFAULT_TENANT_ID);
    t.setName("默认组织");
    t.setSlug("default");
    t.setInviteCode("DEFAULT");
    t.setEnabled(true);
    tenants.save(t);
  }

  private void ensureAdminUser() {
    boolean insecure = gatewayConfig != null && gatewayConfig.allowInsecureDefaults();

    String username = (props == null || props.bootstrapAdminUser() == null) ? "" : props.bootstrapAdminUser().trim();
    String password = (props == null || props.bootstrapAdminPassword() == null) ? "" : props.bootstrapAdminPassword();

    // Fall back to dev defaults when insecure mode is enabled
    if (username.isBlank() && insecure) username = DEV_ADMIN_USER;
    if (password.isBlank() && insecure) password = DEV_ADMIN_PASSWORD;

    if (username.isBlank() || password.isBlank()) return;

    var existing = users.findByUsernameIgnoreCase(username);
    if (existing.isPresent()) {
      // Ensure admin is always ACTIVE (fix PENDING/DISABLED state)
      AppUserEntity admin = existing.get();
      if (admin.getStatus() != UserStatus.ACTIVE) {
        admin.setStatus(UserStatus.ACTIVE);
        users.save(admin);
      }
      return;
    }

    AppUserEntity admin = new AppUserEntity();
    admin.setUsername(username);
    admin.setPasswordHash(encoder.encode(password));
    admin.setRole(UserRole.ADMIN);
    admin.setStatus(UserStatus.ACTIVE);
    admin.setDisplayName("Admin");
    admin.setTenantId(DEFAULT_TENANT_ID);
    users.save(admin);
  }
}
