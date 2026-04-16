package com.codec.kb.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class InternalTokenAuthenticationFilter extends OncePerRequestFilter {
  static final String INTERNAL_HEADER = "X-Internal-Token";

  private final GatewayConfig cfg;

  InternalTokenAuthenticationFilter(GatewayConfig cfg) {
    this.cfg = cfg;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String token = request.getHeader(INTERNAL_HEADER);
    if (token == null || !token.equals(cfg.internalToken())) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
      return;
    }

    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        "internal",
        null,
        AuthorityUtils.createAuthorityList("ROLE_INTERNAL")
    );
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
