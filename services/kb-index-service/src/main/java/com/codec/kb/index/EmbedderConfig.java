package com.codec.kb.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kb.embedder")
public record EmbedderConfig(
    String mode,
    String model,
    String cacheDir,
    Integer maxLength,
    String pooling,
    Boolean normalize
) {
  public String modeOrDefault() {
    return (mode == null || mode.isBlank()) ? "djl" : mode.trim().toLowerCase();
  }

  public String modelOrDefault() {
    return (model == null || model.isBlank())
        ? "Xenova/bge-small-zh-v1.5"
        : model.trim();
  }

  public int maxLengthOrDefault() {
    return (maxLength == null || maxLength <= 0) ? 512 : maxLength;
  }

  public String poolingOrDefault() {
    return (pooling == null || pooling.isBlank()) ? "cls" : pooling.trim().toLowerCase();
  }

  public boolean normalizeOrDefault() {
    return normalize == null || normalize;
  }
}
