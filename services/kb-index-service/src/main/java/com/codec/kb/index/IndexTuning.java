package com.codec.kb.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "index")
public record IndexTuning(
    Bm25 bm25,
    Hybrid hybrid
) {
  public record Bm25(double k1, double b) {}
  public record Hybrid(double bm25Weight, double denseWeight) {}
}
