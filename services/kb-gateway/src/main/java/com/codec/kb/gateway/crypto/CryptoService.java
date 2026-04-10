package com.codec.kb.gateway.crypto;

import com.codec.kb.gateway.CryptoProps;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public final class CryptoService {
  private static final String PREFIX = "v1:";
  private static final int IV_LEN = 12;
  private static final int TAG_BITS = 128;

  private final SecretKey key;
  private final SecureRandom rng = new SecureRandom();

  public CryptoService(CryptoProps props) {
    String mk = (props == null || props.masterKey() == null) ? "" : props.masterKey().trim();
    if (mk.isBlank()) {
      mk = "dev-master-key-change-me"; // StartupSafetyChecks will reject this in production
    }
    this.key = new SecretKeySpec(sha256(mk), "AES");
  }

  public String encrypt(String plaintext) {
    if (plaintext == null) return "";
    String s = plaintext.trim();
    if (s.isEmpty()) return "";

    byte[] iv = new byte[IV_LEN];
    rng.nextBytes(iv);

    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = c.doFinal(s.getBytes(StandardCharsets.UTF_8));

      byte[] out = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ct, 0, out, iv.length, ct.length);

      return PREFIX + Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new RuntimeException("encrypt failed", e);
    }
  }

  public String decrypt(String encrypted) {
    if (encrypted == null) return "";
    String s = encrypted.trim();
    if (s.isEmpty()) return "";
    if (!s.startsWith(PREFIX)) return s;

    byte[] all;
    try {
      all = Base64.getDecoder().decode(s.substring(PREFIX.length()));
    } catch (IllegalArgumentException e) {
      return "";
    }
    if (all.length <= IV_LEN) return "";

    byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
    byte[] ct = Arrays.copyOfRange(all, IV_LEN, all.length);

    try {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] pt = c.doFinal(ct);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "";
    }
  }

  private static byte[] sha256(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
