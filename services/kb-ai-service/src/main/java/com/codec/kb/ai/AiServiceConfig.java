package com.codec.kb.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kb")
public record AiServiceConfig(
    String internalToken,
    String indexUrl
) {}
