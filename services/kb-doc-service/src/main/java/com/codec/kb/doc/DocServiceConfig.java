package com.codec.kb.doc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kb")
public record DocServiceConfig(
    String internalToken,
    String indexUrl,
    String dataDir,
    String ocrUrl
) {}
