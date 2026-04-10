package com.codec.kb.gateway.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

public final class AuthUtil {
  private AuthUtil() {}

  public static AppUserPrincipal requirePrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    Object p = auth.getPrincipal();
    if (p instanceof AppUserPrincipal u) return u;
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
  }
}

