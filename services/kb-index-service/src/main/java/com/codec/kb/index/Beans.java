package com.codec.kb.index;

import com.codec.kb.common.security.InternalAuthAutoConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties({IndexServiceConfig.class, IndexTuning.class, EmbedderConfig.class})
@Import(InternalAuthAutoConfig.class)
public class Beans {

  @Bean
  LocalEmbeddingBackend localEmbeddingBackend(
      EmbedderConfig ec, IndexServiceConfig cfg, ObjectMapper om) {
    String mode = ec.modeOrDefault();
    return switch (mode) {
      case "djl" -> new DjlLocalEmbeddingBackend(ec);
      case "http" -> new HttpLocalEmbeddingBackend(cfg, om);
      default -> throw new IllegalStateException(
          "Unknown kb.embedder.mode='" + mode + "'. Expected 'djl' or 'http'.");
    };
  }
}
