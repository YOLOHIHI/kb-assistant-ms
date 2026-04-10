package com.codec.kb.common.util;

import java.security.MessageDigest;

public final class HashUtil {
  private HashUtil() {}

  public static String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(data);
      byte[] b = md.digest();
      StringBuilder sb = new StringBuilder(b.length * 2);
      for (byte x : b) {
        sb.append(Character.forDigit((x >>> 4) & 0xF, 16));
        sb.append(Character.forDigit(x & 0xF, 16));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
