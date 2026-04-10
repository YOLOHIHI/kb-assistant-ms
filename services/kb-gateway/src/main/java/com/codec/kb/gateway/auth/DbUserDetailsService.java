package com.codec.kb.gateway.auth;

import com.codec.kb.gateway.store.AppUserEntity;
import com.codec.kb.gateway.store.AppUserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public final class DbUserDetailsService implements UserDetailsService {
  private final AppUserRepository users;

  public DbUserDetailsService(AppUserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    AppUserEntity u = users.findByUsernameIgnoreCase(username == null ? "" : username.trim())
        .orElseThrow(() -> new UsernameNotFoundException("user not found"));
    return new AppUserPrincipal(
        u.getId(),
        u.getUsername(),
        u.getPasswordHash(),
        u.getDisplayName(),
        u.getRole(),
        u.getStatus(),
        u.getTenantId(),
        u.isTenantAdmin()
    );
  }
}
