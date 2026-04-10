package com.codec.kb.common.util;

import java.util.UUID;

public final class IdUtil {
  private IdUtil() {}

  public static String newId(String prefix) {
    String id = UUID.randomUUID().toString().replace("-", "");
    return (prefix == null || prefix.isBlank()) ? id : (prefix + "_" + id);
  }
}
