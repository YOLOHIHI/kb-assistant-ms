package com.codec.kb.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the X-Internal-Token header on all internal service endpoints.
 * Registered as a bean via {@link InternalAuthAutoConfig}, imported by each service.
 */
public class InternalAuthFilter extends OncePerRequestFilter {
  private final String internalToken;

  public InternalAuthFilter(String internalToken) {
    if (internalToken == null || internalToken.isBlank()) {
      throw new IllegalArgumentException("kb.internal-token must not be blank");
    }
    this.internalToken = internalToken;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "/internal/health".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String tok = request.getHeader("X-Internal-Token");
    if (tok == null || !tok.equals(internalToken)) {
      response.setStatus(401);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"unauthorized\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
