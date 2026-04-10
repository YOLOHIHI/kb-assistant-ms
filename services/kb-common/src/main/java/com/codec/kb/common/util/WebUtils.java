package com.codec.kb.common.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class WebUtils {
  private WebUtils() {}

  public static String safeTrim(String s) {
    return s == null ? "" : s.trim();
  }

  public static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  /** Returns "" to indicate an invalid/missing kbId; non-blank means valid. */
  public static String safeKbId(String kbId) {
    if (kbId == null) return "";
    String s = kbId.trim();
    if (s.isEmpty()) return "";
    if (!s.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) return "";
    return s;
  }

  /** Validates kbId; throws 400 if invalid or missing. */
  public static String requireKbId(String kbId) {
    String id = safeKbId(kbId);
    if (id.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid kbId");
    }
    return id;
  }

  /** Like safeKbId but returns "default" instead of "" — safe for filesystem use. */
  public static String safeKbDir(String kbId) {
    String id = safeKbId(kbId);
    return id.isEmpty() ? "default" : id;
  }
}
